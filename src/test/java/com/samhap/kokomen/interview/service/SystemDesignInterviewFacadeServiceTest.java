package com.samhap.kokomen.interview.service;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerResultResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignInterviewService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignProceedStateStore;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemDesignInterviewFacadeServiceTest extends BaseTest {

    private static final String ROOT_QUESTION_METRIC_NAME = "root_question_interview_count";
    private static final String ANSWER_CONTENT = "API 서버 앞에 Redis 캐시를 두고, 캐시에 없을 때만 DB를 읽어요.";
    private static final String TEMPORARILY_UNAVAILABLE_MESSAGE = "일시적인 문제로 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";

    @Autowired
    private SystemDesignInterviewFacadeService systemDesignInterviewFacadeService;
    @Autowired
    private SystemDesignInterviewService systemDesignInterviewService;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewTestSupport interviewTestSupport;
    @Autowired
    private SystemDesignProceedStateStore stateStore;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private SystemDesignLlmBulkhead llmBulkhead;
    @Autowired
    private RedisService redisService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // TRUNCATE로 테이블마다 id가 1부터 다시 시작해도 interview와 system_design_interview의 id가 같아지지 않게
    // 다른 회원의 일반 면접을 먼저 둔다. interview id 대신 system_design_interview id로 찾는 실수가 드러난다.
    // 출제하지 않는(INACTIVE) 문제도 하나 먼저 두어 문제 id와 system_design_interview id도 어긋나게 한다.
    @BeforeEach
    void setUpOtherMemberInterview() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(otherMember)
                .rootQuestion(rootQuestion)
                .build());
    }

    // 루트 질문 메트릭 애스펙트는 기존 시작 파사드에만 걸린다. 시스템 설계 시작에 걸리면 루트 질문 id 조회가 null을 돌려
    // root_question_id="null" 계열이 생긴다. MeterRegistry는 캐시된 컨텍스트에서 테스트끼리 공유되므로 합계의 변화만 본다.
    @Test
    void 시스템_설계_면접_시작은_루트_질문_메트릭을_올리지_않는다() {
        // given
        Member member = saveMemberWithTokens(6);
        problemTestSupport.saveProblem();
        double before = sumRootQuestionMetric();

        // when
        systemDesignInterviewFacadeService.startInterview(new MemberAuth(member.getId()));

        // then
        assertThat(sumRootQuestionMetric()).isEqualTo(before);
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있어도_시작할_수_있다() {
        // given
        Member member = saveMemberWithTokens(6);
        problemTestSupport.saveProblem();
        String lockKey = lockKeyOf(member);
        redisService.acquireLockWithValue(lockKey, "other-request", Duration.ofMinutes(5));

        // when
        SystemDesignInterviewStartResponse response = systemDesignInterviewFacadeService.startInterview(
                new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(systemDesignInterviewRepository.findByInterviewId(response.interviewId())).isPresent(),
                () -> assertThat(redisService.get(lockKey, String.class)).contains("other-request")
        );
    }

    // 1번 기준 질문이 없는 문제라 락을 잡은 뒤 완료 처리에서 실패한다.
    @Test
    void 락_안에서_실패해도_진행_락을_풀고_단계를_되돌린다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problemWithoutQuestions = problemRepository.save(
                SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member, problemWithoutQuestions,
                SystemDesignStage.REQUIREMENTS);
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(interviewId,
                new MemberAuth(member.getId())))
                .isInstanceOf(IllegalStateException.class);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(questionRepository.count()).isZero()
        );
    }

    @Test
    void 락_해제가_실패해도_요구사항_정리_결과를_돌려준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when
        SystemDesignRequirementsCompleteResponse response = systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(response.stage()).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(response.curQuestionId()).isEqualTo(1),
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isPresent()
        );
    }

    // 요구사항 정리 완료는 소유자 → 단계 → 진행 락 순서로 검사하므로 락이 잡혀 있어도 단계 오류가 먼저다.
    @Test
    void 다른_요청이_진행_락을_쥐고_있어도_단계가_틀리면_단계_오류를_먼저_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(member.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 설계 단계예요.");
        assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request");
    }

    @Test
    void 다른_회원의_면접이면_단계가_틀려도_403을_먼저_준다() {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(owner,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(otherMember.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 면접만 볼 수 있어요.");
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있으면_409이고_락은_그대로다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 역질문은 소유자 → 단계 → 길이 → 개수를 진행 락보다 먼저 본다. 락을 기다리게 하지 않고 흔한 오류를 바로 알려 준다.
    @Test
    void 역질문은_다른_요청이_진행_락을_쥐고_있어도_단계_길이_개수_오류를_먼저_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview designStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview requirementStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview fullInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order <= SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(fullInterview, order);
        }
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> askRequirementQuestion(designStageInterview, member, "하루에 몇 개나요?"))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요."),
                () -> assertThatThrownBy(() -> askRequirementQuestion(requirementStageInterview, member,
                        "가".repeat(201)))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문은 1~200자로 적어 주세요."),
                () -> assertThatThrownBy(() -> askRequirementQuestion(fullInterview, member, "하루에 몇 개나요?"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요."),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any())
        );
    }

    // 사전 검사와 락 획득 사이에 요구사항 정리가 끝나면 락 안에서 다시 준비하다 409로 실패한다. 이때도 락을 풀어야 한다.
    @Test
    void 락을_잡는_사이에_요구사항_정리가_끝나면_락_안에서_409이고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        Long interviewId = systemDesignInterview.getInterview().getId();
        AtomicBoolean completedBeforeLock = new AtomicBoolean();
        willAnswer(invocation -> {
            if (completedBeforeLock.compareAndSet(false, true)) {
                systemDesignInterviewService.completeRequirements(interviewId, member.getId());
            }
            return invocation.callRealMethod();
        }).given(redissonClient).getBucket(lockKeyOf(member), StringCodec.INSTANCE);

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("요구사항 단계가 끝났어요.");
        assertAll(
                () -> assertThat(completedBeforeLock).isTrue(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 클라이언트는 전송 실패와 응답 검증 실패를 모두 ExternalApiException(내부 문구)으로 올린다. 사용자에게는 정해진 문구만 보인다.
    @Test
    void LLM이_실패하면_사용자_문구로_500이고_저장하지_않으며_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExternalApiException llmFailure = new ExternalApiException(
                "시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_daily_creation_volume");
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willThrow(llmFailure);

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("질문에 답하지 못했어요. 잠시 후 다시 시도해 주세요.")
                .hasCause(llmFailure);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)
        );
    }

    @Test
    void LLM_호출에서_예상하지_못한_예외가_나도_저장하지_않고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any()))
                .willThrow(new IllegalStateException("예상하지 못한 오류"));

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(IllegalStateException.class);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 앱의 503(global.exception.ServiceUnavailableException)은 LLM 실패(500)로 바꾸지 않고 그대로 내보낸다.
    @Test
    void 동시_실행_허가를_못_받으면_503이고_500으로_바뀌지_않으며_락을_푼다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExecutorService executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> occupants = new ArrayList<>();
        try {
            for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
                occupants.add(executor.submit(() -> llmBulkhead.execute(() -> {
                    acquired.countDown();
                    return awaitQuietly(release);
                })));
            }
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .isNotInstanceOf(ExternalApiException.class)
                    .hasMessage("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요.");
            assertAll(
                    () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                    () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                    () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
            );
        } finally {
            release.countDown();
            for (Future<?> occupant : occupants) {
                occupant.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
        assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
    }

    // 허가를 기다리는 요청 스레드가 인터럽트되어도(예: 강제 종료) 503으로 끝나고 진행 락을 풀며 인터럽트 플래그를 남긴다.
    @Test
    void 동시_실행_허가를_기다리다_인터럽트되면_503이고_락을_푼다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExecutorService executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> occupants = new ArrayList<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfterThrow = new AtomicBoolean();
        try {
            for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
                occupants.add(executor.submit(() -> llmBulkhead.execute(() -> {
                    acquired.countDown();
                    return awaitQuietly(release);
                })));
            }
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            Thread requestThread = new Thread(() -> {
                try {
                    askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");
                } catch (RuntimeException e) {
                    thrown.set(e);
                    interruptedAfterThrow.set(Thread.currentThread().isInterrupted());
                }
            });
            requestThread.start();
            // 진행 락을 잡은 뒤 허가를 기다리는 중(TIMED_WAITING)일 때 인터럽트한다.
            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(20))
                    .until(() -> redisService.get(lockKeyOf(member), String.class).isPresent()
                            && requestThread.getState() == Thread.State.TIMED_WAITING);

            // when
            requestThread.interrupt();
            requestThread.join(Duration.ofSeconds(5).toMillis());
        } finally {
            release.countDown();
            for (Future<?> occupant : occupants) {
                occupant.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }

        // then
        assertAll(
                () -> assertThat(thrown.get()).isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요."),
                () -> assertThat(interruptedAfterThrow).isTrue(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)
        );
    }

    @Test
    void 락_해제가_실패해도_역질문_답을_돌려준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when
        SystemDesignRequirementAnswerResponse response = askRequirementQuestion(systemDesignInterview, member,
                "하루에 몇 개나 만들어지나요?");

        // then
        assertAll(
                () -> assertThat(response.answer()).isEqualTo("하루 평균 10만 개 정도예요."),
                () -> assertThat(response.remainingCount()).isEqualTo(4),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isPresent()
        );
    }

    // 락 안에서 실패해도(저장 직전 개수 재확인) 락을 풀고 행을 남기지 않는다.
    @Test
    void LLM을_기다리는_동안_5번째_역질문이_저장되면_400이고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order < SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order);
        }
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willAnswer(invocation -> {
            saveRequirementQuestion(systemDesignInterview, SystemDesignRequirementQuestion.MAX_COUNT);
            return new SystemDesignRequirementAnswerResult(List.of(), "");
        });

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "다섯 번째 역질문"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("역질문은 5개까지 할 수 있어요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .hasSize(SystemDesignRequirementQuestion.MAX_COUNT)
                        .extracting(SystemDesignRequirementQuestion::getContent)
                        .doesNotContain("다섯 번째 역질문")
        );
    }

    // 첫 요청은 저장까지 끝났는데 응답을 받지 못해(클라이언트 타임아웃, 네트워크 오류) 같은 질문을 다시 보낸 경우다.
    @Test
    void 응답을_받지_못해_같은_역질문을_다시_보내면_LLM을_다시_부르지_않고_저장된_응답을_그대로_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        SystemDesignRequirementAnswerResponse first = askRequirementQuestion(systemDesignInterview, member,
                "하루에 몇 개나 만들어지나요?");

        // when
        SystemDesignRequirementAnswerResponse retried = askRequirementQuestion(systemDesignInterview, member,
                "  하루에 몇 개나 만들어지나요?  ");

        // then
        assertAll(
                () -> assertThat(retried).isEqualTo(first),
                () -> assertThat(retried.remainingCount()).isEqualTo(4),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1),
                () -> verify(systemDesignRequirementBedrockClient, times(1)).answerRequirement(any()),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 저장한_지_60초가_지난_뒤_같은_역질문을_보내면_새로_답하고_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");
        moveSavedAtBack(readRequirementQuestions(systemDesignInterview).get(0), 61);

        // when
        SystemDesignRequirementAnswerResponse response = askRequirementQuestion(systemDesignInterview, member,
                "하루에 몇 개나 만들어지나요?");

        // then
        assertAll(
                () -> assertThat(response.remainingCount()).isEqualTo(3),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .extracting(SystemDesignRequirementQuestion::getQuestionOrder,
                                SystemDesignRequirementQuestion::getContent)
                        .containsExactly(tuple(1, "하루에 몇 개나 만들어지나요?"), tuple(2, "하루에 몇 개나 만들어지나요?")),
                () -> verify(systemDesignRequirementBedrockClient, times(2)).answerRequirement(any())
        );
    }

    @Test
    void 직전과_다른_역질문은_60초_안이어도_새로_답하고_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of(), "등록되지 않은 내용이에요. 가정하고 진행하세요."));
        askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");

        // when
        SystemDesignRequirementAnswerResponse response = askRequirementQuestion(systemDesignInterview, member,
                "하루에 몇 개나 조회되나요?");

        // then
        assertAll(
                () -> assertThat(response.question()).isEqualTo("하루에 몇 개나 조회되나요?"),
                () -> assertThat(response.remainingCount()).isEqualTo(3),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(2),
                () -> verify(systemDesignRequirementBedrockClient, times(2)).answerRequirement(any())
        );
    }

    // 5번째 역질문의 응답을 받지 못해 다시 보낸 경우. 락 밖 사전 검사도 락 안 준비도 개수 오류(400)를 내지 않는다.
    @Test
    void 역질문_5개를_다_쓴_뒤_직전과_같은_질문을_다시_보내면_400이_아니라_저장된_응답을_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order < SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order);
        }
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of(), "등록되지 않은 내용이에요. 가정하고 진행하세요."));
        SystemDesignRequirementAnswerResponse fifth = askRequirementQuestion(systemDesignInterview, member,
                "다섯 번째 역질문");

        // when
        SystemDesignRequirementAnswerResponse retried = askRequirementQuestion(systemDesignInterview, member,
                "다섯 번째 역질문");

        // then
        assertAll(
                () -> assertThat(retried).isEqualTo(fifth),
                () -> assertThat(retried.remainingCount()).isZero(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .hasSize(SystemDesignRequirementQuestion.MAX_COUNT),
                () -> verify(systemDesignRequirementBedrockClient, times(1)).answerRequirement(any()),
                () -> assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "여섯 번째 역질문"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요.")
        );
    }

    // 재시도인지는 락 안의 준비에서 정한다. 첫 요청을 처리하는 중(락을 쥔 동안)에 다시 보낸 요청은 지금처럼 409다.
    @Test
    void 다른_요청이_진행_락을_쥐고_있으면_직전과_같은_역질문도_409다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1)
        );
    }

    // 다른 회원은 면접 주인의 직전 역질문과 같은 질문을 보내도 그 응답을 받지 못한다.
    @Test
    void 다른_회원이_직전과_같은_역질문을_보내면_403이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, otherMember, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 면접만 볼 수 있어요.");
    }

    // 끝난 면접에 빈 답변을 토큰 0개로 내도 끝남 문구가 나와야 상태를 입력·토큰보다 먼저 본 것이다.
    // 응답을 받지 못한 6번째 답변을 다시 보내면, 마지막 토큰을 썼더라도 토큰 부족이 아니라 끝남을 알려야 한다.
    @Test
    void 상태_검사는_입력_검사와_토큰_검증보다_먼저다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(0);
        SystemDesignInterview finishedInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, B, B, B, B, B));
        SystemDesignAnswerRequest emptyAnswer = new SystemDesignAnswerRequest("  ", null);

        // when & then
        assertThatThrownBy(() -> submitAnswer(finishedInterview, member, 6, emptyAnswer))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 끝난 면접이에요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateEnd(any())
        );
    }

    @Test
    void 입력_검사는_토큰_검증보다_먼저다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1,
                        new SystemDesignAnswerRequest("가".repeat(3_001), diagramRequest())))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("답변은 1~3,000자로 적어 주세요."),
                () -> assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1,
                        new SystemDesignAnswerRequest(ANSWER_CONTENT, null)))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("다이어그램 형식이 맞지 않아요."),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    // 다른 요청이 락을 쥐고 있어도 토큰 문구가 나와야 토큰을 락보다 먼저 본 것이다.
    @Test
    void 토큰이_없으면_400이고_락을_잡지_않는다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(0);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1, validRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any()),
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 1)).isEmpty()
        );
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있으면_409이고_답변은_저장되지_않는다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1, validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any()),
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 1)).isEmpty(),
                () -> assertThat(readAnswerCount(systemDesignInterview)).isZero(),
                () -> assertThat(readTokenCount(member)).isEqualTo(6)
        );
    }

    // 202를 받기 전에 다시 보내거나 채점 중에 다시 내는 경우다. 두 번째 제출은 락에서 막히고 답변은 하나만 저장된다.
    @Test
    void 채점_중에_같은_질문을_다시_내면_409다() throws InterruptedException {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        CountDownLatch gradingStarted = new CountDownLatch(1);
        CountDownLatch gradingReleased = new CountDownLatch(1);
        willAnswer(invocation -> {
            gradingStarted.countDown();
            gradingReleased.await(10, TimeUnit.SECONDS);
            return answerEvaluation();
        }).given(systemDesignAnswerBedrockClient).evaluateAnswer(any());
        try {
            submitAnswer(systemDesignInterview, member, 1, validRequest());
            assertThat(gradingStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1, validRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");
        } finally {
            gradingReleased.countDown();
            awaitLockReleased(member);
        }
        assertAll(
                () -> assertThat(findAnswerResult(systemDesignInterview, member, 1).proceedState())
                        .isEqualTo(InterviewProceedState.COMPLETED),
                () -> assertThat(readAnswerCount(systemDesignInterview)).isEqualTo(1),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                () -> verify(systemDesignAnswerBedrockClient, times(1)).evaluateAnswer(any())
        );
    }

    @Test
    void 처리가_끝난_뒤_같은_질문을_다시_내면_지금_질문이_아니에요다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any())).willReturn(answerEvaluation());
        submitAnswer(systemDesignInterview, member, 1, validRequest());
        awaitCompleted(systemDesignInterview, member, 1);

        // when & then
        assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1, validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("지금 질문이 아니에요.");
        assertAll(
                () -> assertThat(readAnswerCount(systemDesignInterview)).isEqualTo(1),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    // 사전 검사와 락 획득 사이에 다른 요청이 같은 질문의 답을 저장하면 락 안의 준비가 409로 실패한다. 이때도 락을 풀어야 한다.
    @Test
    void 락_안_준비에서_실패하면_락을_푼다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        AtomicBoolean answeredBeforeLock = new AtomicBoolean();
        willAnswer(invocation -> {
            if (answeredBeforeLock.compareAndSet(false, true)) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                        .question(readQuestion(systemDesignInterview, 1))
                        .content("다른 요청이 먼저 저장한 답변이에요.")
                        .build());
            }
            return invocation.callRealMethod();
        }).given(redissonClient).getBucket(lockKeyOf(member), StringCodec.INSTANCE);

        // when & then
        assertThatThrownBy(() -> submitAnswer(systemDesignInterview, member, 1, validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("지금 질문이 아니에요.");
        assertAll(
                () -> assertThat(answeredBeforeLock).isTrue(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any()),
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 1)).isEmpty(),
                () -> assertThat(readTokenCount(member)).isEqualTo(6)
        );
    }

    // 시작할 때 6개 보유만 확인하고, 저장에 성공한 답변마다 1개씩 차감해 끝까지 답하면 0개가 남는다.
    @Test
    void 토큰_6개로_시작해_6문항을_모두_답하면_0개가_남는다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        problemTestSupport.saveProblem();
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any())).willReturn(answerEvaluation());
        given(systemDesignAnswerBedrockClient.evaluateEnd(any())).willReturn(endEvaluation());
        MemberAuth memberAuth = new MemberAuth(member.getId());
        Long interviewId = systemDesignInterviewFacadeService.startInterview(memberAuth).interviewId();
        systemDesignInterviewFacadeService.completeRequirements(interviewId, memberAuth);
        List<Integer> tokenCounts = new ArrayList<>();

        // when
        for (int order = 1; order <= 6; order++) {
            systemDesignInterviewFacadeService.submitAnswer(interviewId, order, validRequest(), memberAuth);
            int questionOrder = order;
            await().atMost(Duration.ofSeconds(5)).until(() -> systemDesignInterviewFacadeService
                    .findAnswerResult(interviewId, questionOrder, memberAuth).proceedState()
                    == InterviewProceedState.COMPLETED);
            awaitLockReleased(member);
            tokenCounts.add(readTokenCount(member));
        }

        // then
        SystemDesignInterviewResponse interview = systemDesignInterviewFacadeService.findInterview(interviewId,
                memberAuth);
        assertAll(
                () -> assertThat(tokenCounts).containsExactly(5, 4, 3, 2, 1, 0),
                () -> assertThat(readTokenCount(member, TokenType.PAID)).isZero(),
                () -> assertThat(interview.interviewState()).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(interview.curQuestionCount()).isEqualTo(6),
                () -> assertThat(interview.curQuestionId()).isNull(),
                () -> assertThat(interview.curAnswerProceedState()).isNull(),
                () -> assertThat(systemDesignInterviewFacadeService.findAnswerResult(interviewId, 6, memberAuth))
                        .isEqualTo(new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED,
                                InterviewState.FINISHED, SystemDesignStage.DEEP_DIVE, A, null, null)),
                () -> verify(systemDesignAnswerBedrockClient, times(5)).evaluateAnswer(any()),
                () -> verify(systemDesignAnswerBedrockClient, times(1)).evaluateEnd(any())
        );
    }

    @Test
    void LLM_FAILED_뒤_같은_질문에_다시_내면_실패한_제출은_차감하지_않고_저장된_답만_1개_차감한다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of());
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any()))
                .willThrow(new ExternalApiException("시스템 설계 답변 평가 호출에 실패했습니다."))
                .willReturn(answerEvaluation());

        // when
        submitAnswer(systemDesignInterview, member, 1, validRequest());
        await().atMost(Duration.ofSeconds(5)).until(() -> stateStore.find(interviewIdOf(systemDesignInterview), 1)
                .filter(state -> state == InterviewProceedState.LLM_FAILED)
                .isPresent());
        awaitLockReleased(member);
        int tokenCountAfterFailure = readTokenCount(member);
        SystemDesignAnswerResultResponse failedResult = findAnswerResult(systemDesignInterview, member, 1);
        submitAnswer(systemDesignInterview, member, 1, validRequest());
        awaitCompleted(systemDesignInterview, member, 1);

        // then
        assertAll(
                () -> assertThat(failedResult.proceedState()).isEqualTo(InterviewProceedState.LLM_FAILED),
                () -> assertThat(tokenCountAfterFailure).isEqualTo(6),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                () -> assertThat(readAnswerCount(systemDesignInterview)).isEqualTo(1)
        );
    }

    // 채점을 기다리는 동안 새로고침해도 E2는 진행 락을 기다리지 않고 같은 현재 질문과 채점 중 상태를 준다.
    @Test
    void 채점_중이면_현재_답변_상태가_LLM_PENDING이다() throws InterruptedException {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        CountDownLatch gradingStarted = new CountDownLatch(1);
        CountDownLatch gradingReleased = new CountDownLatch(1);
        willAnswer(invocation -> {
            gradingStarted.countDown();
            gradingReleased.await(10, TimeUnit.SECONDS);
            return answerEvaluation();
        }).given(systemDesignAnswerBedrockClient).evaluateAnswer(any());
        SystemDesignInterviewResponse response;
        try {
            submitAnswer(systemDesignInterview, member, 2, validRequest());
            assertThat(gradingStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            response = findInterview(systemDesignInterview, member);
            assertThat(redisService.get(lockKeyOf(member), String.class)).isPresent();
        } finally {
            gradingReleased.countDown();
            awaitLockReleased(member);
        }

        // then
        assertAll(
                () -> assertThat(response.curQuestionId()).isEqualTo(2),
                () -> assertThat(response.curQuestionCount()).isEqualTo(1),
                () -> assertThat(response.curAnswerProceedState()).isEqualTo("LLM_PENDING")
        );
    }

    @Test
    void 채점이_실패했으면_LLM_FAILED다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        stateStore.markFailed(interviewIdOf(systemDesignInterview), 2);
        // 다른 질문의 상태는 보지 않는다.
        stateStore.markPending(interviewIdOf(systemDesignInterview), 1);

        // when
        SystemDesignInterviewResponse response = findInterview(systemDesignInterview, member);

        // then
        assertThat(response.curAnswerProceedState()).isEqualTo("LLM_FAILED");
    }

    @Test
    void 제출하지_않았으면_NONE이다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));

        // when
        SystemDesignInterviewResponse response = findInterview(systemDesignInterview, member);

        // then
        assertAll(
                () -> assertThat(response.curQuestionId()).isEqualTo(2),
                () -> assertThat(response.curAnswerProceedState()).isEqualTo("NONE")
        );
    }

    @Test
    void 요구사항_단계와_끝난_뒤에는_키가_없다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview requirementsStage = saveSystemDesignInterview(member, problemTestSupport.saveProblem(),
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview finishedInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, B, B, B, B, B));
        stateStore.markPending(interviewIdOf(requirementsStage), 1);
        stateStore.markCompleted(interviewIdOf(finishedInterview), 6);

        // when
        SystemDesignInterviewResponse requirementsResponse = findInterview(requirementsStage, member);
        SystemDesignInterviewResponse finishedResponse = findInterview(finishedInterview, member);

        // then
        assertAll(
                () -> assertThat(requirementsResponse.curQuestionId()).isNull(),
                () -> assertThat(requirementsResponse.curAnswerProceedState()).isNull(),
                () -> assertThat(finishedResponse.curQuestionId()).isNull(),
                () -> assertThat(finishedResponse.curAnswerProceedState()).isNull()
        );
    }

    // 현재 질문을 DB에서 정한 뒤 Redis를 읽기 전에 워커가 끝나면 COMPLETED가 보인다. 프론트가 폴링으로 결과를 받게 채점 중으로 보낸다.
    @Test
    void Redis가_COMPLETED면_LLM_PENDING으로_보낸다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        stateStore.markCompleted(interviewIdOf(systemDesignInterview), 2);

        // when
        SystemDesignInterviewResponse response = findInterview(systemDesignInterview, member);

        // then
        assertThat(response.curAnswerProceedState()).isEqualTo("LLM_PENDING");
    }

    @Test
    void Redis_조회가_실패하면_현재_답변_상태는_NONE이다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        stateStore.markPending(interviewIdOf(systemDesignInterview), 2);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(anyString(), any(Codec.class));

        // when
        SystemDesignInterviewResponse response = findInterview(systemDesignInterview, member);

        // then
        assertAll(
                () -> assertThat(response.curQuestionId()).isEqualTo(2),
                () -> assertThat(response.curAnswerProceedState()).isEqualTo("NONE")
        );
    }

    // Redis 장애 때 진행 락을 잡지 못하면 내부 문구(레디스 예외 발생)의 500 대신 사용자 문구의 503이다. 아무것도 바뀌지 않는다.
    @Test
    void Redis가_실패하면_역질문_요구사항_정리_답변_제출은_사용자_문구의_503이고_아무것도_바뀌지_않는다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = saveMemberWithTokens(6);
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview requirementStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview designStageInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(lockKeyOf(member), StringCodec.INSTANCE);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> askRequirementQuestion(requirementStageInterview, member,
                        "하루에 몇 개나 만들어지나요?"))
                        .isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE),
                () -> assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(
                        interviewIdOf(requirementStageInterview), new MemberAuth(member.getId())))
                        .isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE),
                () -> assertThatThrownBy(() -> submitAnswer(designStageInterview, member, 2, validRequest()))
                        .isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE)
        );
        assertAll(
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any()),
                () -> assertThat(readRequirementQuestions(requirementStageInterview)).isEmpty(),
                () -> assertThat(readStage(requirementStageInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(readAnswerCount(designStageInterview)).isEqualTo(1),
                () -> assertThat(readTokenCount(member)).isEqualTo(6)
        );
    }

    // 답이 없는 질문은 Redis의 채점 상태를 읽어야 해서, 읽지 못하면 사용자 문구의 503이다. 면접 조회는 따로 NONE으로 견딘다.
    @Test
    void 답이_없는_질문의_결과를_조회할_때_Redis가_실패하면_사용자_문구의_503이다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(anyString(), any(Codec.class));

        // when & then
        assertThatThrownBy(() -> findAnswerResult(systemDesignInterview, member, 2))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(TEMPORARILY_UNAVAILABLE_MESSAGE);
    }

    // DB에 답이 있으면 Redis를 읽지 않으므로 Redis 장애와 상관없이 COMPLETED다.
    @Test
    void 답이_저장된_질문의_결과는_Redis가_실패해도_COMPLETED다() {
        // given
        interviewTestSupport.saveOtherMemberRecords();
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B));
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(anyString(), any(Codec.class));

        // when
        SystemDesignAnswerResultResponse response = findAnswerResult(systemDesignInterview, member, 1);

        // then
        assertThat(response.proceedState()).isEqualTo(InterviewProceedState.COMPLETED);
    }

    private void submitAnswer(SystemDesignInterview systemDesignInterview, Member member, Integer questionOrder,
                              SystemDesignAnswerRequest request) {
        systemDesignInterviewFacadeService.submitAnswer(interviewIdOf(systemDesignInterview), questionOrder, request,
                new MemberAuth(member.getId()));
    }

    private SystemDesignAnswerResultResponse findAnswerResult(SystemDesignInterview systemDesignInterview,
                                                              Member member, Integer questionOrder) {
        return systemDesignInterviewFacadeService.findAnswerResult(interviewIdOf(systemDesignInterview),
                questionOrder, new MemberAuth(member.getId()));
    }

    private SystemDesignInterviewResponse findInterview(SystemDesignInterview systemDesignInterview, Member member) {
        return systemDesignInterviewFacadeService.findInterview(interviewIdOf(systemDesignInterview),
                new MemberAuth(member.getId()));
    }

    // 워커가 저장을 커밋하면 폴링이 COMPLETED가 된다. 워커는 그 뒤에 락을 푸므로 락이 풀릴 때까지 기다려 다음 요청·테스트와 겹치지 않게 한다.
    private void awaitCompleted(SystemDesignInterview systemDesignInterview, Member member, int questionOrder) {
        await().atMost(Duration.ofSeconds(5)).until(() -> findAnswerResult(systemDesignInterview, member,
                questionOrder).proceedState() == InterviewProceedState.COMPLETED);
        awaitLockReleased(member);
    }

    private void awaitLockReleased(Member member) {
        await().atMost(Duration.ofSeconds(5))
                .until(() -> redisService.get(lockKeyOf(member), String.class).isEmpty());
    }

    private static SystemDesignAnswerRequest validRequest() {
        return new SystemDesignAnswerRequest(ANSWER_CONTENT, diagramRequest());
    }

    private static SystemDesignDiagramRequest diagramRequest() {
        return new SystemDesignDiagramRequest(
                List.of(new SystemDesignDiagramRequest.Block("client", "CLIENT", "Client", List.of(),
                                new SystemDesignDiagramRequest.Position(0.0, 80.0)),
                        new SystemDesignDiagramRequest.Block("api", "API_SERVER", "API 서버",
                                List.of(new SystemDesignDiagramRequest.Prop("개수", "3")),
                                new SystemDesignDiagramRequest.Position(260.0, 80.0))),
                List.of(new SystemDesignDiagramRequest.Connection("c1", "client", "api", "HTTPS")));
    }

    private static SystemDesignAnswerEvaluation answerEvaluation() {
        return new SystemDesignAnswerEvaluation(B, "조회 경로에 캐시를 둔 근거가 분명해요.", null);
    }

    private static SystemDesignEndEvaluation endEvaluation() {
        Map<String, Boolean> metByPointCode = new LinkedHashMap<>();
        for (EvaluationPoint point : SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS) {
            metByPointCode.put(point.code(), true);
        }
        return new SystemDesignEndEvaluation(A, "301과 302를 근거와 함께 비교했어요.", "강점: 근거가 분명해요.", metByPointCode);
    }

    private static Long interviewIdOf(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterview.getInterview().getId();
    }

    private SystemDesignQuestion readQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return questionRepository.findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, questionOrder)
                .orElseThrow();
    }

    private int readAnswerCount(SystemDesignInterview systemDesignInterview) {
        return answerRepository.findByQuestionIn(
                questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview)).size();
    }

    private int readTokenCount(Member member) {
        return readTokenCount(member, TokenType.FREE);
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }

    private SystemDesignRequirementAnswerResponse askRequirementQuestion(SystemDesignInterview systemDesignInterview,
                                                                         Member member, String question) {
        return systemDesignInterviewFacadeService.askRequirementQuestion(systemDesignInterview.getInterview().getId(),
                new SystemDesignRequirementQuestionRequest(question), new MemberAuth(member.getId()));
    }

    private void saveRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .content("역질문 " + questionOrder)
                .build());
    }

    private List<SystemDesignRequirementQuestion> readRequirementQuestions(
            SystemDesignInterview systemDesignInterview) {
        return requirementQuestionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    // 저장 시각(created_at)을 DB 안에서 seconds초 앞으로 옮겨 그만큼 시간이 지난 것처럼 만든다. 자바의 LocalDateTime을 바로 넣으면
    // JPA와 시간대 변환이 달라(JVM이 UTC이고 연결은 Asia/Seoul) 9시간이 더 지난 값으로 읽힌다.
    private void moveSavedAtBack(SystemDesignRequirementQuestion requirementQuestion, long seconds) {
        jdbcTemplate.update("UPDATE system_design_requirement_question "
                        + "SET created_at = DATE_SUB(created_at, INTERVAL ? SECOND) WHERE id = ?",
                seconds, requirementQuestion.getId());
    }

    private static Object awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private double sumRootQuestionMetric() {
        return meterRegistry.find(ROOT_QUESTION_METRIC_NAME).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private SystemDesignInterview saveSystemDesignInterview(Member member, SystemDesignProblem problem,
                                                            SystemDesignStage stage) {
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
        return systemDesignInterviewRepository.save(SystemDesignInterviewFixtureBuilder.builder()
                .interview(interview)
                .problem(problem)
                .stage(stage)
                .build());
    }

    private SystemDesignStage readStage(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterviewRepository.findById(systemDesignInterview.getId()).orElseThrow().getStage();
    }

    private static String lockKeyOf(Member member) {
        return InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }
}
