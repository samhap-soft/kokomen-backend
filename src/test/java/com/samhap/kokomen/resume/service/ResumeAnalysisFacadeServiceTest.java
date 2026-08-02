package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.RedisCleaner;
import com.samhap.kokomen.global.constant.AwsConstant;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.PdfFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisClaimResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionRetryResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitRequest;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisUsageStatusResponse;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import com.samhap.kokomen.resume.tool.ResumeAnalysisPdfPolicy;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class ResumeAnalysisFacadeServiceTest extends BaseTest {

    private static final String RESUME_TEXT = "이력서 원문입니다. Java, Spring Boot 경험이 있습니다.";
    private static final String LINKED_RESUME_BODY = "Portfolio GitHub";
    private static final String LINKED_RESUME_URI = "https://github.com/parity-example";
    // 픽스처 입력(본문·URI)과 별개로 기대 출력을 전부 리터럴로 적는다. 프로덕션이 조립하는 <links> 마크업을
    // 테스트가 같은 방식으로 조립하면 마크업이 바뀌어도 테스트가 따라 바뀌어 아무것도 고정하지 못한다.
    private static final String LINKED_RESUME_TEXT = """
            Portfolio GitHub

            <links>
            https://github.com/parity-example
            </links>""";
    private static final String JOB_POSITION = "백엔드 개발자";
    private static final String JOB_DESCRIPTION = "Java/Spring 기반 서버 개발자를 모집합니다.";
    private static final String JOB_CAREER = "신입";
    private static final String EXTRACTION_FAILED_MESSAGE = "이력서 PDF에서 텍스트를 추출할 수 없습니다.";
    private static final int INITIAL_FREE_TOKEN_COUNT = 20;
    private static final String OTHER_GUEST_TOKEN = "00000000-0000-0000-0000-000000000000";
    private static final Duration SHORTENED_ATTEMPT_TTL = Duration.ofSeconds(30);
    private static final Duration EXECUTOR_DRAIN_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ResumeAnalysisFacadeService resumeAnalysisFacadeService;
    @Autowired
    private ResumeAnalysisStateService resumeAnalysisStateService;
    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired
    private ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberResumeRepository memberResumeRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private RedisService redisService;
    @Autowired
    private RedisCleaner redisCleaner;
    @Autowired
    @Qualifier("resumeAnalysisExecutor")
    private ThreadPoolTaskExecutor resumeAnalysisExecutor;

    // BaseTest가 제공하는 resumeAnalysisAsyncService·pdfValidator·pdfTextExtractor 목은 재선언하지 않는다.
    // 같은 타입을 BaseTest와 서브클래스에 동시 선언하면 Spring이 중복 오버라이드를 거부해 컨텍스트 기동 자체가 실패한다.
    // 로컬 목은 ResumeAnalysisPdfPolicy 하나뿐이며, 이력서 분석 컨트롤러 테스트도 같은 1개를 선언해
    // 컨텍스트 캐시 키를 공유한다.
    @MockitoBean
    private ResumeAnalysisPdfPolicy resumeAnalysisPdfPolicy;

    @BeforeEach
    void setUpExtraction() {
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class))).willReturn(RESUME_TEXT);
    }

    // MySQLDatabaseCleaner는 DB만 지운다. BaseTest는 각 테스트 '전에' Redis를 비우므로 클래스의 마지막
    // 테스트가 남긴 365일 게스트 락은 다음 클래스까지 살아남는다(DocsTest는 Redis를 비우지 않는다).
    @AfterEach
    void clearGuestLocks() {
        redisCleaner.clearAllRedisData();
    }

    @Test
    void 회원이_이력서_파일로_분석을_제출하면_PENDING_행과_원문이_저장되고_비동기가_시작된다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithJd());

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000)).run(commandCaptor.capture());
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(response.guestToken()).isNull(),
                () -> assertThat(saved.getState()).isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(saved.isGuest()).isFalse(),
                () -> assertThat(saved.getGuestToken()).isNull(),
                () -> assertThat(saved.getGuestLockValue()).isNull(),
                () -> assertThat(saved.isJdProvided()).isTrue(),
                () -> assertThat(saved.isBillingRequired()).isFalse(),
                () -> assertThat(saved.getMemberResume()).isNotNull(),
                () -> assertThat(resumeAnalysisRepository.existsChargeableByMemberId(member.getId())).isTrue(),
                () -> assertThat(resumeAnalysisSourceTextRepository.existsByAnalysisId(saved.getId())).isTrue(),
                () -> assertThat(commandCaptor.getValue().analysisId()).isEqualTo(saved.getId()),
                () -> assertThat(commandCaptor.getValue().billingMemberId()).isNull(),
                () -> assertThat(commandCaptor.getValue().jdProvided()).isTrue(),
                () -> assertThat(commandCaptor.getValue().resumeText()).isEqualTo(RESUME_TEXT),
                () -> assertThat(redisTemplate.keys(
                        ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + "*")).isEmpty()
        );
    }

    @Test
    void 회원이_JD_없이_제출하면_jd_provided가_false로_저장되고_커맨드에도_false가_실린다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd());

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000)).run(commandCaptor.capture());
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(saved.isJdProvided()).isFalse(),
                () -> assertThat(commandCaptor.getValue().jdProvided()).isFalse(),
                () -> assertThat(commandCaptor.getValue().jobDescription()).isNull()
        );
    }

    @Test
    void 저장된_이력서_ID로_제출하면_기존_content를_재사용하고_파일_추출을_호출하지_않는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        MemberResume memberResume = memberResumeRepository.save(MemberResumeFixtureBuilder.builder()
                .member(member)
                .content("저장된 이력서 원문")
                .build());
        ResumeAnalysisSubmitRequest request = new ResumeAnalysisSubmitRequest(
                null, null, memberResume.getId(), null, JOB_POSITION, null, JOB_CAREER);

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), request);

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000)).run(commandCaptor.capture());
        verify(pdfTextExtractor, never()).extractTextWithLinks(any(MultipartFile.class));
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(commandCaptor.getValue().resumeText()).isEqualTo("저장된 이력서 원문"),
                () -> assertThat(saved.getMemberResume()).isNotNull(),
                () -> assertThat(resumeAnalysisSourceTextRepository.existsByAnalysisId(saved.getId())).isTrue()
        );
    }

    // 제출 방식이 점수를 바꾸면 안 된다. 링크가 annotation으로만 걸린 이력서를 파일로 내면 <links>가 보이고
    // resume_id로 내면 안 보이던 결함의 회귀 가드다. 두 원문을 서로 비교하는 것만으로는 둘 다 null인 경우도
    // 통과하므로 각각을 리터럴과 대조한다.
    @Test
    void 같은_PDF는_파일로_내든_저장된_이력서_ID로_내든_링크까지_같은_원문으로_추출된다() {
        // given
        byte[] pdf = linkedResumePdf();
        delegateExtractionToRealExtractor();
        stubS3Download(pdf);
        Member uploader = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Member reuser = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        MemberResume savedResume = memberResumeRepository.save(MemberResumeFixtureBuilder.builder()
                .member(reuser)
                .resumeUrl(AwsConstant.CLOUD_FRONT_DOMAIN_URL + "resume/reuse.pdf")
                .content(null)
                .build());

        // when
        Long uploadedId = resumeAnalysisFacadeService.submitMemberAnalysis(uploader.getId(),
                new ResumeAnalysisSubmitRequest(
                        new MockMultipartFile("resume", "resume.pdf", "application/pdf", pdf), null, null, null,
                        JOB_POSITION, null, JOB_CAREER)).analysisId();
        Long reusedId = resumeAnalysisFacadeService.submitMemberAnalysis(reuser.getId(),
                new ResumeAnalysisSubmitRequest(null, null, savedResume.getId(), null,
                        JOB_POSITION, null, JOB_CAREER)).analysisId();

        // then
        String uploadedText = readSourceResumeContent(uploadedId);
        String reusedText = readSourceResumeContent(reusedId);
        assertAll(
                () -> assertThat(uploadedText).isEqualTo(LINKED_RESUME_TEXT),
                () -> assertThat(reusedText).isEqualTo(LINKED_RESUME_TEXT),
                () -> assertThat(reusedText).isEqualTo(uploadedText)
        );
    }

    @Test
    void 진행_중_분석이_있으면_제출할_수_없다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        resumeAnalysisFacadeService.submitMemberAnalysis(member.getId(), fileRequestWithoutJd());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitMemberAnalysis(
                        member.getId(), fileRequestWithoutJd()))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("이미 진행 중인 이력서 분석이 있습니다."),
                () -> assertThat(resumeAnalysisRepository.count()).isEqualTo(1L)
        );
    }

    @Test
    void 토큰이_부족하면_분석_행이_저장되지_않는다() {
        // given — 첫 제출은 무료(billingRequired=false)이므로 토큰 0개로도 통과한다. 완료 처리 후
        // 두 번째 제출에서 비로소 유료 판정(existsChargeableByMemberId=true)이 걸린다.
        Member member = saveMemberWithTokens(0);
        ResumeAnalysisSubmitResponse first = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd());
        completeAnalysis(first.analysisId());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitMemberAnalysis(
                        member.getId(), fileRequestWithoutJd()))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("토큰 갯수가 부족합니다."),
                () -> assertThat(resumeAnalysisRepository.count()).isEqualTo(1L)
        );
    }

    @Test
    void 신규_회원은_첫_사용이_무료다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);

        // when
        ResumeAnalysisUsageStatusResponse response = resumeAnalysisFacadeService.findUsageStatus(member.getId());

        // then
        assertAll(
                () -> assertThat(response.firstUseFree()).isTrue(),
                () -> assertThat(response.tokenCost()).isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void claim된_게스트_분석이_있어도_회원의_첫_사용은_무료다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.71");
        resumeAnalysisFacadeService.claimGuestAnalysis(guest.guestToken(), new MemberAuth(member.getId()));
        completeAnalysis(guest.analysisId());

        // when
        ResumeAnalysisUsageStatusResponse usageStatus = resumeAnalysisFacadeService.findUsageStatus(member.getId());
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd());

        // then
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(usageStatus.firstUseFree()).isTrue(),
                () -> assertThat(saved.isBillingRequired()).isFalse()
        );
    }

    /**
     * 과금 주체 불변식 1: 회원 제출의 billingMemberId는 인증 주체 그 자신이다.
     *
     * <p>chargeTokensIfNeeded는 billingMemberId를 행의 소유자와 대조하지 않고 그대로 신뢰한다. 따라서 "누가
     * 차감되는가"는 이 파사드가 커맨드에 실은 값 하나로 결정된다. 커맨드에 실린 값이 그대로 도착했는지만 보면
     * 파사드가 엉뚱한 회원을 골라도 통과하므로, 캡처한 값으로 실제 차감을 돌려 잔량으로 확정한다.
     * 방관자 회원의 잔량이 그대로인 단정이 "인증 주체가 아닌 누군가가 차감되는" 경로를 배제한다.
     */
    @Test
    void 유료_제출의_과금_회원은_인증_주체이며_다른_회원의_토큰은_차감되지_않는다() {
        // given — 첫 제출은 무료다. 완료 처리한 뒤 두 번째 제출부터 유료 판정이 걸린다.
        Member payer = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Member bystander = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        completeAnalysis(resumeAnalysisFacadeService.submitMemberAnalysis(
                payer.getId(), fileRequestWithoutJd()).analysisId());

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitMemberAnalysis(
                payer.getId(), fileRequestWithoutJd());

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000).times(2)).run(commandCaptor.capture());
        ResumeAnalysisCommand billableCommand = commandCaptor.getAllValues().get(1);
        resumeAnalysisStateService.chargeTokensIfNeeded(response.analysisId(), billableCommand.billingMemberId());
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(billableCommand.isBillable()).isTrue(),
                () -> assertThat(billableCommand.billingMemberId()).isEqualTo(payer.getId()),
                () -> assertThat(saved.isBillingRequired()).isTrue(),
                () -> assertThat(readFreeTokenCount(payer))
                        .isEqualTo(INITIAL_FREE_TOKEN_COUNT
                                - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(readFreeTokenCount(bystander)).isEqualTo(INITIAL_FREE_TOKEN_COUNT)
        );
    }

    /**
     * 과금 주체 불변식 2: 게스트는 과금되지 않는다.
     *
     * <p>워커의 readCommand는 게스트 여부와 무관하게 billingMemberId를 null로 고정하므로, 워커 쪽에서 이
     * 불변식을 단정하면 게스트성과 무관한 이유로 초록이 된다. 게스트성이 실제로 billingMemberId를 결정하는
     * 지점은 이 파사드뿐이고(회원 경로는 유료일 때 회원 id를 싣는다 — 위 테스트), 그래서 여기서 단정한다.
     * 캡처한 커맨드로 실제 과금을 돌려 차감된 회원이 아무도 없고 행의 charged_token_count가 0인 것까지 본다.
     */
    @Test
    void 게스트_제출은_과금_대상이_아니어서_어떤_회원의_토큰도_차감되지_않는다() {
        // given
        Member bystander = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitGuestAnalysis(
                fileRequestWithoutJd(), new ClientIp("11.22.33.83"));

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000)).run(commandCaptor.capture());
        ResumeAnalysisCommand guestCommand = commandCaptor.getValue();
        resumeAnalysisStateService.chargeTokensIfNeeded(response.analysisId(), guestCommand.billingMemberId());
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(guestCommand.isBillable()).isFalse(),
                () -> assertThat(guestCommand.billingMemberId()).isNull(),
                () -> assertThat(saved.isGuest()).isTrue(),
                () -> assertThat(saved.isBillingRequired()).isFalse(),
                () -> assertThat(saved.getChargedTokenCount()).isZero(),
                () -> assertThat(readFreeTokenCount(bystander)).isEqualTo(INITIAL_FREE_TOKEN_COUNT)
        );
    }

    @Test
    void 게스트가_제출하면_member_id는_null이고_guest_token과_별개의_락_값이_저장된다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.72");

        // when
        ResumeAnalysisSubmitResponse response = resumeAnalysisFacadeService.submitGuestAnalysis(
                fileRequestWithJd(), clientIp);

        // then
        verify(resumeAnalysisAsyncService, timeout(2_000)).run(any(ResumeAnalysisCommand.class));
        ResumeAnalysis saved = resumeAnalysisRepository.findById(response.analysisId()).orElseThrow();
        String lockKey = ResumeAnalysisFacadeService.createGuestLockKey(clientIp);
        assertAll(
                () -> assertThat(response.guestToken()).isNotNull(),
                () -> assertThat(saved.isGuest()).isTrue(),
                () -> assertThat(saved.getGuestToken()).isEqualTo(response.guestToken()),
                () -> assertThat(saved.getGuestIp()).isEqualTo(clientIp.address()),
                () -> assertThat(saved.getGuestLockValue()).isNotNull(),
                () -> assertThat(saved.getGuestLockValue()).isNotEqualTo(saved.getGuestToken()),
                () -> assertThat(saved.isBillingRequired()).isFalse(),
                () -> assertThat(saved.getMemberResume()).isNull(),
                () -> assertThat(resumeAnalysisSourceTextRepository.existsByAnalysisId(saved.getId())).isTrue(),
                () -> assertThat(redisService.get(lockKey, String.class)).contains(saved.getGuestLockValue())
        );
    }

    @Test
    void 같은_IP의_게스트가_두_번_제출하면_예외가_발생한다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.73");
        resumeAnalysisFacadeService.submitGuestAnalysis(fileRequestWithoutJd(), clientIp);

        // when & then
        assertAll(
                () -> assertThat(redisTemplate.hasKey(
                        ResumeAnalysisFacadeService.createGuestLockKey(clientIp)))
                        .isTrue(),
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                        fileRequestWithoutJd(), clientIp))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("비회원 이력서 분석은 1회만 가능합니다."),
                () -> assertThat(resumeAnalysisRepository.count()).isEqualTo(1L)
        );
    }

    @Test
    void 추출이_실패하면_게스트_락을_잡지_않는다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.74");
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class))).willReturn(null);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                        fileRequestWithoutJd(), clientIp))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(EXTRACTION_FAILED_MESSAGE),
                () -> assertThat(redisTemplate.hasKey(
                        ResumeAnalysisFacadeService.createGuestLockKey(clientIp)))
                        .isFalse(),
                () -> assertThat(resumeAnalysisRepository.count()).isZero()
        );
    }

    @Test
    void 게스트_시간당_시도_한도를_초과하면_예외가_발생한다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.75");
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class))).willReturn(null);
        for (int attempt = 1; attempt <= ResumeAnalysisFacadeService.GUEST_MAX_ATTEMPTS_PER_HOUR; attempt++) {
            assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                    fileRequestWithoutJd(), clientIp))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(EXTRACTION_FAILED_MESSAGE);
        }

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                        fileRequestWithoutJd(), clientIp))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
                () -> assertThat(redisTemplate.hasKey(
                        ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX + clientIp.address()))
                        .isTrue()
        );
    }

    @Test
    void 게스트는_저장된_이력서_ID를_사용할_수_없다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.76");
        ResumeAnalysisSubmitRequest request = new ResumeAnalysisSubmitRequest(
                null, null, 1L, null, JOB_POSITION, null, JOB_CAREER);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(request, clientIp))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("비회원은 저장된 이력서를 사용할 수 없습니다."),
                () -> assertThat(redisTemplate.hasKey(
                        ResumeAnalysisFacadeService.createGuestLockKey(clientIp)))
                        .isFalse(),
                () -> assertThat(resumeAnalysisRepository.count()).isZero()
        );
    }

    @Test
    void 미claim_게스트_분석을_회원이_claim하면_member_id가_채워지고_guest_token은_남는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.77");

        // when
        ResumeAnalysisClaimResponse response = resumeAnalysisFacadeService.claimGuestAnalysis(
                guest.guestToken(), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(response.analysisId()).isEqualTo(guest.analysisId()),
                () -> assertThat(response.state()).isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(resumeAnalysisRepository.existsByMemberIdAndGuestTokenIsNotNull(member.getId()))
                        .isTrue(),
                () -> assertThat(resumeAnalysisRepository.findByGuestToken(guest.guestToken())).isPresent()
        );
    }

    @Test
    void 본인이_이미_claim한_분석을_다시_claim하면_같은_응답을_받는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.78");
        resumeAnalysisFacadeService.claimGuestAnalysis(guest.guestToken(), new MemberAuth(member.getId()));

        // when
        ResumeAnalysisClaimResponse response = resumeAnalysisFacadeService.claimGuestAnalysis(
                guest.guestToken(), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(response.analysisId()).isEqualTo(guest.analysisId()),
                () -> assertThat(response.state()).isEqualTo(ResumeAnalysisState.PENDING)
        );
    }

    @Test
    void 다른_회원이_claim한_분석을_claim하면_403이다() {
        // given
        Member owner = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Member other = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.79");
        resumeAnalysisFacadeService.claimGuestAnalysis(guest.guestToken(), new MemberAuth(owner.getId()));

        // when & then
        assertThatThrownBy(() -> resumeAnalysisFacadeService.claimGuestAnalysis(
                guest.guestToken(), new MemberAuth(other.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("이미 다른 회원에게 귀속된 이력서 분석입니다.");
    }

    @Test
    void 존재하지_않는_guest_token으로_claim하면_404다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);

        // when & then
        assertThatThrownBy(() -> resumeAnalysisFacadeService.claimGuestAnalysis(
                "00000000-0000-0000-0000-000000000000", new MemberAuth(member.getId())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("존재하지 않는 이력서 분석입니다.");
    }

    @Test
    void 이미_비회원_분석을_연결한_회원은_추가_claim이_400이다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse first = submitGuest("11.22.33.80");
        ResumeAnalysisSubmitResponse second = submitGuest("11.22.33.81");
        resumeAnalysisFacadeService.claimGuestAnalysis(first.guestToken(), new MemberAuth(member.getId()));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.claimGuestAnalysis(
                        second.guestToken(), new MemberAuth(member.getId())))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("이미 연결된 비회원 분석이 있습니다."),
                () -> assertThat(resumeAnalysisRepository.findByGuestToken(second.guestToken())
                        .orElseThrow()
                        .isGuest()).isTrue()
        );
    }

    @Test
    void 평가만_완료된_게스트_분석도_claim할_수_있다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.82");
        completeEvaluationOnly(guest.analysisId());

        // when
        ResumeAnalysisClaimResponse response = resumeAnalysisFacadeService.claimGuestAnalysis(
                guest.guestToken(), new MemberAuth(member.getId()));

        // then
        assertThat(response.state()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED);
    }

    @Test
    void QUESTION_FAILED에서_재시도하면_EVALUATION_COMPLETED로_복원되고_질문_hop만_다시_실행된다() {
        // given — readCommand 스텁은 일부러 과금 회원이 실린 커맨드를 돌려준다. 파사드가 재시도 커맨드에서
        // 과금 회원을 벗겨내지 않으면 아래 billingMemberId 단정이 깨진다(재시도는 무과금 규약).
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Long analysisId = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd()).analysisId();
        failQuestions(analysisId);
        given(resumeAnalysisAsyncService.readCommand(analysisId))
                .willReturn(command(analysisId, member.getId()));

        // when
        ResumeAnalysisQuestionRetryResponse response = resumeAnalysisFacadeService.retryQuestionGeneration(
                analysisId, new MemberAuth(member.getId()), null);

        // then
        ArgumentCaptor<ResumeAnalysisCommand> commandCaptor = ArgumentCaptor.forClass(ResumeAnalysisCommand.class);
        verify(resumeAnalysisAsyncService, timeout(2_000))
                .runQuestionHop(commandCaptor.capture(), any(ResumeAnalysisEvaluation.class));
        ResumeAnalysis reloaded = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(response.analysisId()).isEqualTo(analysisId),
                () -> assertThat(response.state()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(response.questionRetryCount()).isEqualTo(1),
                () -> assertThat(reloaded.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(reloaded.getQuestionRetryCount()).isEqualTo(1),
                () -> assertThat(commandCaptor.getValue().billingMemberId()).isNull(),
                () -> assertThat(commandCaptor.getValue().analysisId()).isEqualTo(analysisId)
        );
    }

    @Test
    void 재시도_상한을_초과하면_400을_반환한다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Long analysisId = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd()).analysisId();
        ResumeAnalysis analysis = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        analysis.completeEvaluation(evaluationWithoutJd());
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        analysis.restoreForQuestionRetry();
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        analysis.restoreForQuestionRetry();
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        resumeAnalysisRepository.save(analysis);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                        analysisId, new MemberAuth(member.getId()), null))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문 재생성 가능 횟수를 초과했습니다."),
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow().getQuestionRetryCount())
                        .isEqualTo(2)
        );
    }

    @Test
    void COMPLETED_상태에서_재시도하면_400을_반환한다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Long analysisId = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd()).analysisId();
        completeAnalysis(analysisId);

        // when & then
        assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                analysisId, new MemberAuth(member.getId()), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("질문 재생성이 필요한 상태가 아닙니다.");
    }

    @Test
    void 다른_회원의_분석은_재시도할_수_없다() {
        // given
        Member owner = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Member other = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Long analysisId = resumeAnalysisFacadeService.submitMemberAnalysis(
                owner.getId(), fileRequestWithoutJd()).analysisId();
        failQuestions(analysisId);

        // when & then
        assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                analysisId, new MemberAuth(other.getId()), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 이력서 분석만 조회할 수 있습니다.");
    }

    /**
     * 미claim 게스트 행의 유일한 인증 수단은 guest_token이다. 이 검사가 없으면 analysisId를 아는 누구나
     * 남의 비회원 분석을 재생성시킬 수 있다.
     */
    @Test
    void 게스트_분석은_guest_token이_일치하지_않으면_재시도할_수_없다() {
        // given
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.84");
        failQuestions(guest.analysisId());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                        guest.analysisId(), MemberAuth.notAuthenticated(), OTHER_GUEST_TOKEN))
                        .isInstanceOf(ForbiddenException.class)
                        .hasMessage("본인의 이력서 분석만 조회할 수 있습니다."),
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                        guest.analysisId(), MemberAuth.notAuthenticated(), null))
                        .isInstanceOf(ForbiddenException.class)
                        .hasMessage("본인의 이력서 분석만 조회할 수 있습니다."),
                () -> verify(resumeAnalysisAsyncService, never())
                        .runQuestionHop(any(ResumeAnalysisCommand.class), any(ResumeAnalysisEvaluation.class)),
                () -> assertThat(resumeAnalysisRepository.findById(guest.analysisId()).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(resumeAnalysisRepository.findById(guest.analysisId()).orElseThrow()
                        .getQuestionRetryCount()).isZero()
        );
    }

    @Test
    void 게스트_분석은_올바른_guest_token으로_재시도할_수_있다() {
        // given
        ResumeAnalysisSubmitResponse guest = submitGuest("11.22.33.85");
        failQuestions(guest.analysisId());
        given(resumeAnalysisAsyncService.readCommand(guest.analysisId()))
                .willReturn(command(guest.analysisId(), null));

        // when
        ResumeAnalysisQuestionRetryResponse response = resumeAnalysisFacadeService.retryQuestionGeneration(
                guest.analysisId(), MemberAuth.notAuthenticated(), guest.guestToken());

        // then
        verify(resumeAnalysisAsyncService, timeout(2_000))
                .runQuestionHop(any(ResumeAnalysisCommand.class), any(ResumeAnalysisEvaluation.class));
        ResumeAnalysis reloaded = resumeAnalysisRepository.findById(guest.analysisId()).orElseThrow();
        assertAll(
                () -> assertThat(response.state()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(response.questionRetryCount()).isEqualTo(1),
                () -> assertThat(reloaded.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(reloaded.getQuestionRetryCount()).isEqualTo(1)
        );
    }

    /**
     * 고정 시간창 단정. 남은 시간을 짧게 덮어써 두고 상한까지 시도를 더 쌓았을 때 그 값이 밀리지 않아야 한다.
     * 매 시도마다 TTL을 새로 걸면(슬라이딩 창) 이 단정이 만료 시간 전체 길이를 보게 되어 깨진다.
     */
    @Test
    void 게스트_시도_카운터는_고정_시간창이라_후속_시도가_TTL을_연장하지_않는다() {
        // given — 추출 실패로 끝나는 시도도 카운터에는 잡힌다.
        ClientIp clientIp = new ClientIp("11.22.33.86");
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class))).willReturn(null);
        String attemptKey =
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX + clientIp.address();
        assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(fileRequestWithoutJd(), clientIp))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(EXTRACTION_FAILED_MESSAGE);
        redisTemplate.expire(attemptKey, SHORTENED_ATTEMPT_TTL);

        // when — 상한까지 시도를 더 쌓는다.
        for (int attempt = 2; attempt <= ResumeAnalysisFacadeService.GUEST_MAX_ATTEMPTS_PER_HOUR; attempt++) {
            assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                    fileRequestWithoutJd(), clientIp))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage(EXTRACTION_FAILED_MESSAGE);
        }

        // then
        assertAll(
                () -> assertThat(redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS))
                        .isPositive()
                        .isLessThanOrEqualTo(SHORTENED_ATTEMPT_TTL.toMillis()),
                () -> assertThatThrownBy(() -> resumeAnalysisFacadeService.submitGuestAnalysis(
                        fileRequestWithoutJd(), clientIp))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.")
        );
    }

    /**
     * executor가 태스크를 거절하면 질문 hop은 한 번도 실행되지 않는다. 그런데 상태 복원은 이미 끝나 있어
     * 되돌리지 않으면 실행된 적 없는 시도가 재생성 횟수를 하나 소모한다.
     *
     * <p>목을 쓰지 않고 실제 executor를 정원(최대 스레드 + 큐)까지 블로킹 태스크로 채워 거절을 만든다.
     * executor에 스파이를 걸면 컨텍스트가 하나 더 뜬다.
     */
    @Test
    void executor가_포화되어_재시도가_거절되면_재생성_횟수를_소모하지_않는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Long analysisId = resumeAnalysisFacadeService.submitMemberAnalysis(
                member.getId(), fileRequestWithoutJd()).analysisId();
        failQuestions(analysisId);
        given(resumeAnalysisAsyncService.readCommand(analysisId)).willReturn(command(analysisId, null));
        CountDownLatch blocker = new CountDownLatch(1);

        // when & then
        try {
            saturateResumeAnalysisExecutor(blocker);
            assertThatThrownBy(() -> resumeAnalysisFacadeService.retryQuestionGeneration(
                    analysisId, new MemberAuth(member.getId()), null))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.");
        } finally {
            blocker.countDown();
            awaitResumeAnalysisExecutorDrain();
        }
        ResumeAnalysis reloaded = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(reloaded.getQuestionRetryCount()).isZero(),
                () -> assertThat(reloaded.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(reloaded.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.CAPACITY),
                () -> verify(resumeAnalysisAsyncService, never())
                        .runQuestionHop(any(ResumeAnalysisCommand.class), any(ResumeAnalysisEvaluation.class))
        );
    }

    /**
     * 앞선 테스트가 남긴 태스크를 먼저 흘려보낸 뒤, 모든 스레드가 태스크를 붙들고 큐도 꽉 찰 때까지 블로킹
     * 태스크를 채운다. 채운 태스크는 전부 latch를 기다리므로 어떤 슬롯도 스스로 비지 않고, 포화가 latch를
     * 풀 때까지 유지된다.
     *
     * <p>첫 거절을 포화의 증거로 삼으면 안 된다. 미리 띄워 둔 코어 스레드가 아직 태스크를 집어 들지 않은
     * 순간에는 큐가 잠깐 가득 차 거절이 나고, 곧 스레드가 하나를 집어 가 큐에 자리가 생긴다. 그래서 거절이
     * 나도 물러서지 않고 포화 조건 자체를 확인할 때까지 계속 채운다.
     */
    private void saturateResumeAnalysisExecutor(CountDownLatch blocker) {
        awaitResumeAnalysisExecutorDrain();
        ThreadPoolExecutor pool = resumeAnalysisExecutor.getThreadPoolExecutor();
        long deadline = System.nanoTime() + EXECUTOR_DRAIN_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && !isSaturated(pool)) {
            try {
                resumeAnalysisExecutor.execute(() -> awaitQuietly(blocker));
            } catch (TaskRejectedException e) {
                Thread.onSpinWait();
            }
        }
        if (!isSaturated(pool)) {
            throw new IllegalStateException("이력서 분석 executor를 포화시키지 못했다.");
        }
    }

    private boolean isSaturated(ThreadPoolExecutor pool) {
        return pool.getActiveCount() >= pool.getMaximumPoolSize() && pool.getQueue().remainingCapacity() == 0;
    }

    private void awaitQuietly(CountDownLatch blocker) {
        try {
            blocker.await(EXECUTOR_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitResumeAnalysisExecutorDrain() {
        ThreadPoolExecutor pool = resumeAnalysisExecutor.getThreadPoolExecutor();
        long deadline = System.nanoTime() + EXECUTOR_DRAIN_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline && (pool.getActiveCount() > 0 || !pool.getQueue().isEmpty())) {
            Thread.onSpinWait();
        }
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private int readFreeTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE)
                .orElseThrow()
                .getTokenCount();
    }

    private ResumeAnalysisSubmitResponse submitGuest(String ip) {
        return resumeAnalysisFacadeService.submitGuestAnalysis(fileRequestWithoutJd(), new ClientIp(ip));
    }

    private ResumeAnalysisSubmitRequest fileRequestWithJd() {
        return new ResumeAnalysisSubmitRequest(pdfFile(), null, null, null,
                JOB_POSITION, JOB_DESCRIPTION, JOB_CAREER);
    }

    private ResumeAnalysisSubmitRequest fileRequestWithoutJd() {
        return new ResumeAnalysisSubmitRequest(pdfFile(), null, null, null,
                JOB_POSITION, null, JOB_CAREER);
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("resume", "resume.pdf", "application/pdf",
                "pdf-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] linkedResumePdf() {
        return PdfFixtureBuilder.builder()
                .page(LINKED_RESUME_BODY, List.of(LINKED_RESUME_URI))
                .build();
    }

    // BaseTest의 pdfTextExtractor는 목이라 스텁하지 않으면 무엇을 넣어도 null이다. 추출 결과 자체를 보는
    // 테스트만 실 구현으로 위임한다 — 두 오버로드를 모두 위임해야 어느 제출 경로가 어느 메서드를 부르는지가
    // 결과에 드러난다.
    private void delegateExtractionToRealExtractor() {
        PdfTextExtractor realExtractor = new PdfTextExtractor();
        given(pdfTextExtractor.extractTextWithLinks(any(MultipartFile.class)))
                .willAnswer(invocation ->
                        realExtractor.extractTextWithLinks((MultipartFile) invocation.getArgument(0)));
        given(pdfTextExtractor.extractTextWithLinks(any(byte[].class)))
                .willAnswer(invocation -> realExtractor.extractTextWithLinks((byte[]) invocation.getArgument(0)));
    }

    private void stubS3Download(byte[] pdf) {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), pdf));
    }

    private String readSourceResumeContent(Long analysisId) {
        return resumeAnalysisSourceTextRepository.findByAnalysisId(analysisId).orElseThrow().getResumeContent();
    }

    private ResumeAnalysisCommand command(Long analysisId, Long billingMemberId) {
        return new ResumeAnalysisCommand(analysisId, billingMemberId, false, RESUME_TEXT, null,
                JOB_POSITION, null, JOB_CAREER);
    }

    private void completeEvaluationOnly(Long analysisId) {
        ResumeAnalysis analysis = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        analysis.completeEvaluation(evaluationWithoutJd());
        resumeAnalysisRepository.save(analysis);
    }

    private void completeAnalysis(Long analysisId) {
        ResumeAnalysis analysis = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        analysis.completeEvaluation(evaluationWithoutJd());
        analysis.completeQuestions();
        resumeAnalysisRepository.save(analysis);
    }

    private void failQuestions(Long analysisId) {
        ResumeAnalysis analysis = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        analysis.completeEvaluation(evaluationWithoutJd());
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        resumeAnalysisRepository.save(analysis);
    }

    private ResumeAnalysisEvaluation evaluationWithoutJd() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                dimensionScore(90), dimensionScore(80), dimensionScore(70), dimensionScore(60), null,
                null, "종합 총평입니다.");
        return evaluation.withTotalScore(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluation));
    }

    private DimensionScore dimensionScore(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }
}
