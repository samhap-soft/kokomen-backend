package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.annotation.DistributedLock;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.service.resume.ResumeContentService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import com.samhap.kokomen.resume.service.dto.ExtractedContents;
import com.samhap.kokomen.resume.service.dto.GuestInfo;
import com.samhap.kokomen.resume.service.dto.MaterialRefs;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisClaimResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionRetryResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitRequest;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisSubmitResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisUsageStatusResponse;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import com.samhap.kokomen.resume.tool.PdfValidator;
import com.samhap.kokomen.resume.tool.ResumeAnalysisPdfPolicy;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이력서 분석의 제출·귀속(claim)·질문 재생성·사용 현황 조회를 오케스트레이션한다.
 * 다른 도메인은 이 파사드만 의존하고 내부 서비스를 직접 부르지 않는다.
 *
 * <p>제출은 요청 스레드에서 검증·텍스트 추출·저장까지 끝내고 LLM 2콜만 {@code resumeAnalysisExecutor}로 넘긴다.
 * 제출 메서드에는 {@code @Transactional}을 붙이지 않는다 — 행 저장은 {@code ResumeAnalysisService.saveAnalysis}의
 * {@code REQUIRES_NEW} 안에서만 일어나 반환 시점에 이미 커밋되어 있으므로, executor에 제출한 워커가 행을 못
 * 보는 창이 열리지 않는다. 읽기 전용인 {@code findUsageStatus}와 조건부 UPDATE 후 재조회가 필요한
 * {@code claimGuestAnalysis}만 트랜잭션 경계를 갖는다.
 *
 * <p>과금 주체를 정하는 유일한 지점이다. {@code ResumeAnalysisStateService.chargeTokensIfNeeded}는 넘겨받은
 * {@code billingMemberId}를 행의 소유자와 대조하지 않고 그대로 신뢰하고, 워커도 커맨드의 값을 그대로 흘려보내기만
 * 한다. 그래서 게스트 경로는 항상 null을, 회원 경로는 유료 판정일 때만 인증받은 {@code memberId}를,
 * 재시도 경로는 {@code withoutBilling}으로 null을 싣는다.
 *
 * <p>게스트 락 키·TTL·시도 한도·토큰 비용 상수의 선언 위치이기도 하다. {@code ResumeAnalysisStateService}와
 * 테스트는 이 상수를 참조만 한다. 값을 두 곳에 복제하면 한 경로가 걸어 둔 락을 다른 경로가 다른 키로 해제하는
 * 결함을 테스트가 초록으로 통과시킨다.
 */
@Slf4j
@Service
public class ResumeAnalysisFacadeService {

    public static final String GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX = "guest:resume-analysis:started:";
    public static final Duration GUEST_RESUME_ANALYSIS_LOCK_TTL = Duration.ofDays(365);
    public static final String GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX = "guest:resume-analysis:attempt:";
    public static final int GUEST_MAX_ATTEMPTS_PER_HOUR = 5;
    public static final int RESUME_ANALYSIS_TOKEN_COST = 5;

    private static final Duration GUEST_ATTEMPT_WINDOW = Duration.ofHours(1);
    private static final Duration IN_PROGRESS_WINDOW = Duration.ofMinutes(15);
    private static final List<ResumeAnalysisState> IN_PROGRESS_STATES = List.of(
            ResumeAnalysisState.PENDING, ResumeAnalysisState.EVALUATION_COMPLETED);
    private static final int MAX_CONCURRENT_EXTRACTIONS = 6;
    private static final Semaphore EXTRACTION_SEMAPHORE = new Semaphore(MAX_CONCURRENT_EXTRACTIONS);
    private static final Duration EXTRACTION_ACQUIRE_TIMEOUT = Duration.ofSeconds(2);
    private static final String CAPACITY_MESSAGE = "이력서 분석 요청이 많아 잠시 후 다시 시도해주세요.";
    private static final String FORBIDDEN_MESSAGE = "본인의 이력서 분석만 조회할 수 있습니다.";

    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeAnalysisStateService resumeAnalysisStateService;
    private final ResumeAnalysisAsyncService resumeAnalysisAsyncService;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final MemberService memberService;
    private final TokenFacadeService tokenFacadeService;
    private final RedisService redisService;
    private final PdfValidator pdfValidator;
    private final ResumeAnalysisPdfPolicy resumeAnalysisPdfPolicy;
    private final PdfTextExtractor pdfTextExtractor;
    private final PdfUploadService pdfUploadService;
    private final ResumeContentService resumeContentService;
    private final ThreadPoolTaskExecutor resumeAnalysisExecutor;

    /**
     * {@code @RequiredArgsConstructor}를 쓰지 않는 이유는 executor 주입에 파라미터 {@code @Qualifier}가
     * 필요하기 때문이다. 애플리케이션에 executor 빈이 여러 개라 타입만으로는 결정되지 않는다.
     */
    public ResumeAnalysisFacadeService(
            ResumeAnalysisService resumeAnalysisService,
            ResumeAnalysisStateService resumeAnalysisStateService,
            ResumeAnalysisAsyncService resumeAnalysisAsyncService,
            ResumeAnalysisRepository resumeAnalysisRepository,
            ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository,
            MemberResumeRepository memberResumeRepository,
            MemberPortfolioRepository memberPortfolioRepository,
            MemberService memberService,
            TokenFacadeService tokenFacadeService,
            RedisService redisService,
            PdfValidator pdfValidator,
            ResumeAnalysisPdfPolicy resumeAnalysisPdfPolicy,
            PdfTextExtractor pdfTextExtractor,
            PdfUploadService pdfUploadService,
            ResumeContentService resumeContentService,
            @Qualifier("resumeAnalysisExecutor")
            ThreadPoolTaskExecutor resumeAnalysisExecutor
    ) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.resumeAnalysisStateService = resumeAnalysisStateService;
        this.resumeAnalysisAsyncService = resumeAnalysisAsyncService;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.resumeAnalysisSourceTextRepository = resumeAnalysisSourceTextRepository;
        this.memberResumeRepository = memberResumeRepository;
        this.memberPortfolioRepository = memberPortfolioRepository;
        this.memberService = memberService;
        this.tokenFacadeService = tokenFacadeService;
        this.redisService = redisService;
        this.pdfValidator = pdfValidator;
        this.resumeAnalysisPdfPolicy = resumeAnalysisPdfPolicy;
        this.pdfTextExtractor = pdfTextExtractor;
        this.pdfUploadService = pdfUploadService;
        this.resumeContentService = resumeContentService;
        this.resumeAnalysisExecutor = resumeAnalysisExecutor;
    }

    public static String createGuestLockKey(ClientIp clientIp) {
        return GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX + clientIp.address();
    }

    /**
     * 과금 대상 여부를 여기서 확정한다. 무료 1회를 소진한 회원만 유료이고, 그때 워커로 넘기는 커맨드의
     * {@code billingMemberId}는 이 메서드가 받은 인증 주체 {@code memberId} 그 자신이다. 무료 제출은 null을 실어
     * 워커의 과금 CAS 자체가 시작되지 않게 한다.
     */
    @DistributedLock(prefix = "resume-analysis", key = "#memberId")
    public ResumeAnalysisSubmitResponse submitMemberAnalysis(Long memberId, ResumeAnalysisSubmitRequest request) {
        validateFiles(request);
        validateNoInProgressAnalysis(memberId);
        boolean billingRequired = !isFirstUse(memberId);
        if (billingRequired) {
            tokenFacadeService.validateEnoughTokens(memberId, RESUME_ANALYSIS_TOKEN_COST);
        }
        MaterialRefs savedRefs = findSavedMaterials(memberId, request);
        ExtractedContents contents = extractContents(request, savedRefs);
        MaterialRefs refs = persistMaterialsIfNeeded(memberId, request, contents, savedRefs);
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(memberId, GuestInfo.none(), refs, contents,
                request.toJobInput(), billingRequired);
        submitPipeline(saved, billingRequired ? memberId : null, contents);
        return ResumeAnalysisSubmitResponse.ofMember(saved.getId());
    }

    /**
     * 게스트 경로는 {@code @DistributedLock}을 쓰지 않고 회원 경로와 별 메서드로 분리한다.
     * {@code DistributedLockAspect}는 SpEL 결과가 null이면 {@code BadRequestException}을 던지므로
     * {@code memberId == null}인 게스트를 {@code key = "#memberId"} 메서드에 태울 수 없다.
     * 동시성 제어는 아래 {@code setIfAbsent} 1회성 락이 겸한다.
     *
     * <p>게스트는 과금되지 않는다. 커맨드의 {@code billingMemberId}는 항상 null이고 행의
     * {@code billing_required}도 false다.
     */
    public ResumeAnalysisSubmitResponse submitGuestAnalysis(ResumeAnalysisSubmitRequest request, ClientIp clientIp) {
        validateGuestAttemptQuota(clientIp);
        if (request.hasSavedMaterialId()) {
            throw new BadRequestException("비회원은 저장된 이력서를 사용할 수 없습니다.");
        }
        validateFiles(request);
        ExtractedContents contents = extractContents(request, MaterialRefs.empty());

        // 락은 추출 이후·INSERT 직전에 잡는다. 추출 전에 잡으면 10~60초짜리 추출 구간에서 프로세스가 급사할 때
        // catch도 실행되지 않고 guest_lock_value가 아직 DB에 없어 해당 IP가 365일 영구 차단되고 추적 수단이
        // 0이 된다. 획득 로그가 수동 DEL 런북을 성립시킨다(잔여 위험 구간은 단일 INSERT, 수 ms).
        String lockKey = createGuestLockKey(clientIp);
        String lockValue = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithValue(lockKey, lockValue, GUEST_RESUME_ANALYSIS_LOCK_TTL)) {
            throw new BadRequestException("비회원 이력서 분석은 1회만 가능합니다.");
        }
        log.info("게스트 이력서 분석 락 획득 - lockKey: {}, lockValue: {}", lockKey, lockValue);
        try {
            String guestToken = UUID.randomUUID().toString();
            ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(null,
                    new GuestInfo(guestToken, clientIp, lockValue), MaterialRefs.empty(), contents,
                    request.toJobInput(), false);
            submitPipeline(saved, null, contents);
            return ResumeAnalysisSubmitResponse.ofGuest(saved.getId(), guestToken);
        } catch (RuntimeException e) {
            // failEvaluation(CAPACITY)이 이미 해제한 경우에도 releaseLockSafely는 Lua CAS라 무해하다.
            redisService.releaseLockSafely(lockKey, lockValue);
            throw e;
        }
    }

    private void submitPipeline(ResumeAnalysis analysis, Long billingMemberId, ExtractedContents contents) {
        ResumeAnalysisCommand command = new ResumeAnalysisCommand(
                analysis.getId(), billingMemberId, analysis.isJdProvided(),
                contents.resumeText(), contents.portfolioText(),
                analysis.getJobPosition(), analysis.getJobDescription(), analysis.getJobCareer());
        try {
            resumeAnalysisExecutor.execute(() -> resumeAnalysisAsyncService.run(command));
        } catch (TaskRejectedException e) {
            log.error("이력서 분석 executor 포화 - analysisId: {}", analysis.getId(), e);
            resumeAnalysisStateService.failEvaluation(analysis.getId(), ResumeAnalysisFailureReason.CAPACITY);
            throw new ServiceUnavailableException(CAPACITY_MESSAGE);
        }
    }

    // 락은 1회 '성공' 제한, 카운터는 '시도' 제한이다. 실패하는 PDF를 반복 제출해
    // Tomcat 스레드를 PDFBox에 묶는 경로를 막는다.
    private void validateGuestAttemptQuota(ClientIp clientIp) {
        String attemptKey = GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX + clientIp.address();
        Long attempts = redisService.incrementKey(attemptKey);
        redisService.expireKey(attemptKey, GUEST_ATTEMPT_WINDOW);
        if (attempts > GUEST_MAX_ATTEMPTS_PER_HOUR) {
            log.warn("게스트 이력서 분석 시도 한도 초과 - ip: {}, attempts: {}", clientIp.address(), attempts);
            throw new BadRequestException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void validateFiles(ResumeAnalysisSubmitRequest request) {
        if (request.hasResumeFile()) {
            pdfValidator.validate(request.resume());
            resumeAnalysisPdfPolicy.validatePageCount(request.resume());
        }
        if (request.hasPortfolioFile()) {
            pdfValidator.validate(request.portfolio());
            resumeAnalysisPdfPolicy.validatePageCount(request.portfolio());
        }
    }

    // 15분 시간 창을 두는 이유는 고착된 행 하나가 회원을 영구 제출 차단하지 않게 하는 것이다
    // (스케줄러의 고착 판정 임계값보다 크게 잡는다).
    private void validateNoInProgressAnalysis(Long memberId) {
        if (resumeAnalysisRepository.existsByMemberIdAndStateInAndCreatedAtAfter(memberId, IN_PROGRESS_STATES,
                LocalDateTime.now().minus(IN_PROGRESS_WINDOW))) {
            throw new BadRequestException("이미 진행 중인 이력서 분석이 있습니다.");
        }
    }

    private MaterialRefs findSavedMaterials(Long memberId, ResumeAnalysisSubmitRequest request) {
        MemberResume memberResume = null;
        if (!request.hasResumeFile() && request.resumeId() != null) {
            memberResume = memberResumeRepository.findByIdAndMemberId(request.resumeId(), memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 이력서입니다."));
        }
        MemberPortfolio memberPortfolio = null;
        if (!request.hasPortfolioFile() && request.portfolioId() != null) {
            memberPortfolio = memberPortfolioRepository.findByIdAndMemberId(request.portfolioId(), memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 포트폴리오입니다."));
        }
        return new MaterialRefs(memberResume, memberPortfolio);
    }

    // 동시 추출 수를 Tomcat 스레드 수보다 훨씬 낮게 묶는다. 추출을 CompletableFuture로 병렬화하면
    // 요청 하나가 점유하는 스레드 수가 늘어 이 상한의 의미가 사라진다.
    private ExtractedContents extractContents(ResumeAnalysisSubmitRequest request, MaterialRefs savedRefs) {
        boolean acquired;
        try {
            acquired = EXTRACTION_SEMAPHORE.tryAcquire(EXTRACTION_ACQUIRE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(CAPACITY_MESSAGE);
        }
        if (!acquired) {
            log.warn("이력서 텍스트 추출 동시 실행 한도 초과 - limit: {}", MAX_CONCURRENT_EXTRACTIONS);
            throw new ServiceUnavailableException(CAPACITY_MESSAGE);
        }
        try {
            return doExtract(request, savedRefs);
        } finally {
            EXTRACTION_SEMAPHORE.release();
        }
    }

    private ExtractedContents doExtract(ResumeAnalysisSubmitRequest request, MaterialRefs savedRefs) {
        String resumeText = extractResumeText(request, savedRefs);
        if (resumeText == null || resumeText.isBlank()) {
            throw new BadRequestException("이력서 PDF에서 텍스트를 추출할 수 없습니다.");
        }
        return new ExtractedContents(resumeText, extractPortfolioText(request, savedRefs));
    }

    private String extractResumeText(ResumeAnalysisSubmitRequest request, MaterialRefs savedRefs) {
        if (request.hasResumeFile()) {
            return pdfTextExtractor.extractTextWithLinks(request.resume());
        }
        if (savedRefs.memberResume() == null) {
            throw new BadRequestException("이력서 파일 또는 이력서 ID는 필수입니다.");
        }
        return resumeContentService.getOrExtractResumeContent(savedRefs.memberResume());
    }

    private String extractPortfolioText(ResumeAnalysisSubmitRequest request, MaterialRefs savedRefs) {
        if (request.hasPortfolioFile()) {
            return pdfTextExtractor.extractTextWithLinks(request.portfolio());
        }
        if (savedRefs.memberPortfolio() != null) {
            return resumeContentService.getOrExtractPortfolioContent(savedRefs.memberPortfolio());
        }
        return null;
    }

    private MaterialRefs persistMaterialsIfNeeded(Long memberId, ResumeAnalysisSubmitRequest request,
                                                 ExtractedContents contents, MaterialRefs savedRefs) {
        if (!request.hasResumeFile() && !request.hasPortfolioFile()) {
            return savedRefs;
        }
        Member member = memberService.readById(memberId);
        MemberResume memberResume = savedRefs.memberResume();
        if (request.hasResumeFile()) {
            memberResume = pdfUploadService.saveResume(readBytes(request.resume()),
                    request.resume().getOriginalFilename(), member, contents.resumeText());
        }
        MemberPortfolio memberPortfolio = savedRefs.memberPortfolio();
        if (request.hasPortfolioFile()) {
            memberPortfolio = pdfUploadService.savePortfolio(readBytes(request.portfolio()),
                    request.portfolio().getOriginalFilename(), member, contents.portfolioText());
        }
        return new MaterialRefs(memberResume, memberPortfolio);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("이력서 파일 읽기 실패 - filename: {}", file.getOriginalFilename(), e);
            throw new InternalServerErrorException("이력서 파일을 저장하는 데 실패했습니다.", e);
        }
    }

    /**
     * 멱등 판정을 한도 검사보다 먼저 한다. 순서를 바꾸면 같은 회원의 재claim이
     * {@code validateClaimQuota}에 걸려 400이 되어 재claim 200 멱등 규약과 충돌한다.
     * {@code claimByGuestToken}은 {@code clearAutomatically = true}이므로 아래 재조회는 1차 캐시가 아니라
     * UPDATE 결과가 반영된 DB 값을 본다 — 403 판정이 이 속성에 의존한다.
     */
    @Transactional
    public ResumeAnalysisClaimResponse claimGuestAnalysis(String guestToken, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        ResumeAnalysis found = readByGuestToken(guestToken);
        if (found.isOwner(member.getId())) {
            return new ResumeAnalysisClaimResponse(found.getId(), found.getState());
        }
        validateClaimQuota(member.getId());
        resumeAnalysisRepository.claimByGuestToken(member, guestToken);
        ResumeAnalysis claimed = readByGuestToken(guestToken);
        if (!claimed.isOwner(member.getId())) {
            throw new ForbiddenException("이미 다른 회원에게 귀속된 이력서 분석입니다.");
        }
        return new ResumeAnalysisClaimResponse(claimed.getId(), claimed.getState());
    }

    private ResumeAnalysis readByGuestToken(String guestToken) {
        return resumeAnalysisRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이력서 분석입니다."));
    }

    private void validateClaimQuota(Long memberId) {
        if (resumeAnalysisRepository.existsByMemberIdAndGuestTokenIsNotNull(memberId)) {
            throw new BadRequestException("이미 연결된 비회원 분석이 있습니다.");
        }
    }

    /**
     * {@code @DistributedLock}은 응답 반환 시점에 풀리므로 비동기 작업을 보호하지 않는다. 중복 실행을 막는 실체는
     * {@code restoreForQuestionRetry}의 {@code WHERE state = 'QUESTION_FAILED'} 조건부 전이다.
     *
     * <p>질문 hop은 평가가 커밋된 행에서만 실행되어야 한다. 여기서 그것이 성립하는 근거는 두 단계다.
     * {@code restoreForQuestionRetry}가 1행을 갱신했다는 것은 직전 상태가 {@code QUESTION_FAILED}였다는 뜻이고,
     * 그 상태는 평가 커밋을 거친 {@code EVALUATION_COMPLETED}에서만 올 수 있다. 이어 부르는
     * {@code readEvaluation}은 스스로 상태가 평가 공개 상태인지 검사해 아니면 예외를 던진다.
     * 따라서 이 경로가 hop에 넘기는 평가는 DB에 커밋된 값이다.
     *
     * <p>재시도는 무과금이다. {@code readCommand}가 이미 null을 채워 오지만 그 값에 기대지 않고
     * {@code withoutBilling}으로 명시적으로 벗겨 낸다.
     */
    @DistributedLock(prefix = "resume-analysis-retry", key = "#analysisId")
    public ResumeAnalysisQuestionRetryResponse retryQuestionGeneration(Long analysisId, MemberAuth memberAuth,
                                                                      String guestToken) {
        ResumeAnalysis analysis = resumeAnalysisService.readById(analysisId);
        validateAccessible(analysis, memberAuth, guestToken);
        validateQuestionRetryable(analysis);
        ResumeAnalysisCommand command = resumeAnalysisAsyncService.readCommand(analysisId);
        resumeAnalysisStateService.restoreForQuestionRetry(analysisId);
        ResumeAnalysisEvaluation evaluation = resumeAnalysisService.readEvaluation(analysisId);
        try {
            resumeAnalysisExecutor.execute(() ->
                    resumeAnalysisAsyncService.runQuestionHop(withoutBilling(command), evaluation));
        } catch (TaskRejectedException e) {
            log.error("이력서 분석 질문 재생성 executor 포화 - analysisId: {}", analysisId, e);
            resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.CAPACITY);
            throw new ServiceUnavailableException(CAPACITY_MESSAGE);
        }
        return new ResumeAnalysisQuestionRetryResponse(analysisId, ResumeAnalysisState.EVALUATION_COMPLETED,
                analysis.getQuestionRetryCount() + 1);
    }

    private void validateQuestionRetryable(ResumeAnalysis analysis) {
        if (analysis.getState() != ResumeAnalysisState.QUESTION_FAILED) {
            throw new BadRequestException("질문 재생성이 필요한 상태가 아닙니다.");
        }
        boolean sourceTextExists = resumeAnalysisSourceTextRepository.existsByAnalysisId(analysis.getId());
        if (!analysis.isQuestionRetryable(sourceTextExists)) {
            throw new BadRequestException("질문 재생성 가능 횟수를 초과했습니다.");
        }
    }

    // 이미 차감된 5토큰은 유지하고 워커의 과금 단계를 다시 돌리지 않는다.
    private static ResumeAnalysisCommand withoutBilling(ResumeAnalysisCommand command) {
        return new ResumeAnalysisCommand(command.analysisId(), null, command.jdProvided(), command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobDescription(), command.jobCareer());
    }

    // guest_token의 인증 효력은 member_id IS NULL 동안만이다. claim 후에는 세션 인증만 허용한다.
    private void validateAccessible(ResumeAnalysis analysis, MemberAuth memberAuth, String guestToken) {
        if (analysis.isGuest()) {
            if (!analysis.isSameGuestToken(guestToken)) {
                throw new ForbiddenException(FORBIDDEN_MESSAGE);
            }
            return;
        }
        if (!memberAuth.isAuthenticated() || !analysis.isOwner(memberAuth.memberId())) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public ResumeAnalysisUsageStatusResponse findUsageStatus(Long memberId) {
        return new ResumeAnalysisUsageStatusResponse(isFirstUse(memberId), RESUME_ANALYSIS_TOKEN_COST);
    }

    /**
     * 무료 1회 판정은 이 회원의 과금 대상 이력서 분석 이력 하나만 본다. 구 질문생성 플로우의 이력은 테이블째
     * 삭제되어 판정 근거로 쓸 수 없기 때문이다. 판정 근거가 하나로 줄어든 결과로, 구 플로우를 유료로 써 본
     * 기존 회원에게도 무료 1회가 다시 부여된다 — 의도한 부작용이 아니라 근거 소실에서 따라오는 과금 정책상의
     * 귀결이다.
     *
     * <p>{@code existsChargeableByMemberId}의 쿼리는 {@code guest_token IS NULL} 조건을 포함하므로
     * claim된 게스트 행은 회원의 무료 1회를 태우지 않는다.
     */
    private boolean isFirstUse(Long memberId) {
        return !resumeAnalysisRepository.existsChargeableByMemberId(memberId);
    }
}
