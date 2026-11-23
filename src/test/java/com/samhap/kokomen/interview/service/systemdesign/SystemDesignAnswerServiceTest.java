package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;
import static com.samhap.kokomen.answer.domain.AnswerRank.D;
import static com.samhap.kokomen.answer.domain.AnswerRank.F;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignAnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationResultRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerPendingResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerResultResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService.SystemDesignAnswerDraft;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerTurn;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignNextBaseQuestion;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class SystemDesignAnswerServiceTest extends BaseTest {

    private static final int MAX_QUESTION_COUNT = 6;
    private static final String ANSWER_CONTENT = "API 서버 앞에 Redis 캐시를 두고, 캐시에 없을 때만 DB를 읽어요.";
    private static final SystemDesignDiagram SUBMITTED_DIAGRAM = SystemDesignDiagramFixture.withCache();
    private static final String TOTAL_FEEDBACK = "강점: 조회 경로를 캐시로 줄였어요.\n\n보완점: 장애 대응을 더 구체적으로 설명해 보세요.";
    private static final String NOT_CURRENT_QUESTION_MESSAGE = "지금 질문이 아니에요.";
    private static final String INTERVIEW_FINISHED_MESSAGE = "이미 끝난 면접이에요.";
    private static final String QUESTION_NOT_FOUND_MESSAGE = "질문을 찾을 수 없어요.";
    // TokenFacadeService의 @DistributedLock(prefix = "token", key = "#memberId")가 쓰는 Redisson 락 이름
    private static final String TOKEN_LOCK_KEY_PREFIX = "lock:token:";
    private static final Duration CONCURRENT_TASK_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private SystemDesignAnswerService answerService;
    @Autowired
    private SystemDesignProceedStateStore stateStore;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignProblemQuestionRepository problemQuestionRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private SystemDesignEvaluationResultRepository evaluationResultRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;

    // TRUNCATE로 테이블마다 id가 1부터 다시 시작해도 값이 우연히 같아지지 않게 다른 회원의 기록을 먼저 둔다.
    // 질문 2개·답변 1개가 있어 이 테스트의 질문 PK는 순번 + 2다(순번 대신 PK로 찾는 실수가 드러난다). 면접 6개를 먼저 두어
    // 면접 id는 질문 순번(1~6)보다 크고, 출제하지 않는 문제 2개로 문제 id와 system_design_interview id도 어긋나게 한다.
    @BeforeEach
    void setUpOtherMemberRecords() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        Interview otherInterview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(otherMember)
                .build());
        SystemDesignInterview otherSystemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(otherInterview)
                        .problem(retiredProblem)
                        .stage(SystemDesignStage.HIGH_LEVEL)
                        .build());
        for (int order = 1; order <= 2; order++) {
            SystemDesignProblemQuestion retiredQuestion = problemQuestionRepository.save(
                    SystemDesignProblemQuestionFixtureBuilder.builder()
                            .problem(retiredProblem)
                            .questionOrder(order)
                            .stage(SystemDesignStage.HIGH_LEVEL)
                            .build());
            SystemDesignQuestion question = questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                    .systemDesignInterview(otherSystemDesignInterview)
                    .problemQuestion(retiredQuestion)
                    .build());
            if (order == 1) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder().question(question).build());
            }
        }
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        for (int i = 0; i < 5; i++) {
            interviewRepository.save(InterviewFixtureBuilder.builder()
                    .member(otherMember)
                    .rootQuestion(rootQuestion)
                    .build());
        }
    }

    @Test
    void 답변을_저장하면_토큰을_1개_차감한다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 1);

        // when
        answerService.saveAnswer(draft, answerEvaluation(B, "방금 그린 API 서버에서 단축 키를 어떻게 만드나요?"));

        // then
        SystemDesignAnswer answer = readAnswer(systemDesignInterview, 1);
        assertAll(
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(5),
                () -> assertThat(answer.getContent()).isEqualTo(ANSWER_CONTENT),
                () -> assertThat(answer.getAnswerRank()).isEqualTo(B),
                () -> assertThat(answer.getFeedback()).isEqualTo("B 등급 피드백이에요."),
                () -> assertThat(answer.getDiagram()).isEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getCurrentDiagram())
                        .isEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(readQuestions(systemDesignInterview))
                        .extracting(SystemDesignQuestion::getQuestionOrder, SystemDesignQuestion::getStage,
                                SystemDesignQuestion::getContent)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL, readBaseQuestion(systemDesignInterview, 1)),
                                tuple(2, SystemDesignStage.HIGH_LEVEL, "방금 그린 API 서버에서 단축 키를 어떻게 만드나요?"))
        );
    }

    @Test
    void 차감할_때_토큰이_모자라면_답변과_다음_질문이_롤백된다() {
        // given
        Member member = saveMemberWithTokens(0);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B));
        SystemDesignDiagram diagramBefore = readSystemDesignInterview(systemDesignInterview).getCurrentDiagram();
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);

        // when & then
        assertThatThrownBy(() -> answerService.saveAnswer(draft, answerEvaluation(A, "다듬은 3번 질문이에요.")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertAll(
                () -> assertThat(readAnswers(systemDesignInterview)).hasSize(1),
                () -> assertThat(readQuestions(systemDesignInterview)).hasSize(2),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getStage())
                        .isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getCurrentDiagram())
                        .isEqualTo(diagramBefore)
                        .isNotEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isZero()
        );
    }

    @Test
    void 마지막_답변에_쓸_토큰이_모자라면_면접을_끝내지_않고_회원_점수도_더하지_않는다() {
        // given
        Member member = saveMemberWithTokens(0, 100);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(A, A, A, A, A));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);

        // when & then
        assertThatThrownBy(() -> answerService.saveLastAnswer(draft, endEvaluation(A, verdicts(5))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        Interview interview = readInterview(systemDesignInterview);
        assertAll(
                () -> assertThat(interview.getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS),
                () -> assertThat(interview.getFinishedAt()).isNull(),
                () -> assertThat(interview.getTotalScore()).isNull(),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getReportScore()).isNull(),
                () -> assertThat(readEvaluationResults(systemDesignInterview)).isEmpty(),
                () -> assertThat(readAnswers(systemDesignInterview)).hasSize(5),
                () -> assertThat(readMemberScore(member)).isEqualTo(100)
        );
    }

    /**
     * 토큰 구매는 토큰 락을 쥔 채 token_purchase INSERT의 FK 검사로 회원 행 S 락을 기다린다. 마지막 답변이 회원 점수 UPDATE로
     * 회원 행 X 락을 쥔 채 차감용 토큰 락을 기다리면 두 쪽이 서로를 기다리다 토큰 락 대기 시간이 지나 차감이 실패한다.
     */
    @Test
    void 토큰_구매_승인_중에_마지막_답변을_차감해도_면접을_끝낸다() throws InterruptedException {
        // given
        Member member = saveMemberWithTokens(20);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, B, B, B, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);
        CountDownLatch confirmStarted = new CountDownLatch(1);
        CountDownLatch confirmReleased = new CountDownLatch(1);
        given(tosspaymentsClient.confirmPayment(any(), any())).willAnswer(invocation -> {
            confirmStarted.countDown();
            confirmReleased.await(CONCURRENT_TASK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return createConfirmedTokenPaymentResponse();
        });
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<?> purchase = executorService.submit(
                    () -> tokenFacadeService.purchaseTokens(member.getId(), createTokenPurchaseRequest()));
            assertThat(confirmStarted.await(CONCURRENT_TASK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            clearInvocations(redissonClient);

            // when
            Future<?> lastAnswer = executorService.submit(
                    () -> answerService.saveLastAnswer(draft, endEvaluation(A, verdicts(5))));
            // 차감이 토큰 락을 요청한 뒤에 결제 승인을 끝내, 차감 전에 실행된 쓰기가 구매의 INSERT보다 먼저 락을 잡게 한다.
            verify(redissonClient, timeout(CONCURRENT_TASK_TIMEOUT.toMillis()))
                    .getLock(TOKEN_LOCK_KEY_PREFIX + member.getId());
            confirmReleased.countDown();

            // then
            assertThat(purchase).succeedsWithin(CONCURRENT_TASK_TIMEOUT);
            assertThat(lastAnswer).succeedsWithin(CONCURRENT_TASK_TIMEOUT);
        } finally {
            // 단언이 중간에 실패해도 멈춰 둔 결제 승인을 풀고 두 작업이 끝나기를 기다려, 다음 테스트에 쓰기가 남지 않게 한다.
            confirmReleased.countDown();
            executorService.shutdown();
            if (!executorService.awaitTermination(CONCURRENT_TASK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
        Interview finishedInterview = readInterview(systemDesignInterview);
        assertAll(
                () -> assertThat(finishedInterview.getInterviewState()).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(readMemberScore(member)).isEqualTo(finishedInterview.getTotalScore()),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(19),
                () -> assertThat(readTokenCount(member, TokenType.PAID))
                        .isEqualTo(TokenProduct.TOKEN_10.getTokenCount())
        );
    }

    @Test
    void 세_번째_답변을_저장하면_단계가_DEEP_DIVE이고_다음_질문은_4번이다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 3);

        // when
        answerService.saveAnswer(draft, answerEvaluation(C, "캐시가 먼저 병목이 되면 어떻게 하나요?"));

        // then
        assertAll(
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getStage())
                        .isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(readQuestions(systemDesignInterview))
                        .extracting(SystemDesignQuestion::getQuestionOrder, SystemDesignQuestion::getStage)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL),
                                tuple(2, SystemDesignStage.HIGH_LEVEL),
                                tuple(3, SystemDesignStage.HIGH_LEVEL),
                                tuple(4, SystemDesignStage.DEEP_DIVE)),
                () -> assertThat(readQuestion(systemDesignInterview, 4).getContent())
                        .isEqualTo("캐시가 먼저 병목이 되면 어떻게 하나요?"),
                () -> assertThat(readInterview(systemDesignInterview).getInterviewState())
                        .isEqualTo(InterviewState.IN_PROGRESS)
        );
    }

    @Test
    void 다음_질문은_LLM_문장을_쓰고_null이면_기준_질문을_쓴다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());

        // when
        answerService.saveAnswer(prepare(systemDesignInterview, member, 1),
                answerEvaluation(B, "방금 그린 API 서버에서 단축 키를 어떻게 만드나요?"));
        answerService.saveAnswer(prepare(systemDesignInterview, member, 2), answerEvaluation(B, null));

        // then
        assertAll(
                () -> assertThat(readQuestion(systemDesignInterview, 2).getContent())
                        .isEqualTo("방금 그린 API 서버에서 단축 키를 어떻게 만드나요?"),
                () -> assertThat(readQuestion(systemDesignInterview, 3).getContent())
                        .isEqualTo(readBaseQuestion(systemDesignInterview, 3)),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(4)
        );
    }

    @Test
    void 여섯_번째_답변이면_면접이_끝나고_단계는_DEEP_DIVE로_남는다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, B, C, A, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);

        // when
        answerService.saveLastAnswer(draft, endEvaluation(C, verdicts(3)));

        // then
        SystemDesignAnswer lastAnswer = readAnswer(systemDesignInterview, MAX_QUESTION_COUNT);
        assertAll(
                () -> assertThat(readInterview(systemDesignInterview).getInterviewState())
                        .isEqualTo(InterviewState.FINISHED),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getStage())
                        .isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getCurrentDiagram())
                        .isEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(readQuestions(systemDesignInterview)).hasSize(MAX_QUESTION_COUNT),
                () -> assertThat(lastAnswer.getAnswerRank()).isEqualTo(C),
                () -> assertThat(lastAnswer.getContent()).isEqualTo(ANSWER_CONTENT),
                () -> assertThat(lastAnswer.getFeedback()).isEqualTo("C 등급 피드백이에요."),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isZero()
        );
    }

    @Test
    void 끝나면_interview가_FINISHED이고_finished_at과_등급_점수_합과_종합_피드백이_저장된다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, B, C, A, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);

        // when
        answerService.saveLastAnswer(draft, endEvaluation(C, verdicts(3)));

        // then
        Interview interview = readInterview(systemDesignInterview);
        assertAll(
                () -> assertThat(interview.getInterviewState()).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(interview.getFinishedAt()).isNotNull(),
                // B 10 + B 10 + C 0 + A 20 + B 10 + C 0
                () -> assertThat(interview.getTotalScore()).isEqualTo(50),
                () -> assertThat(interview.getTotalFeedback()).isEqualTo(TOTAL_FEEDBACK)
        );
    }

    @Test
    void 끝나면_평가_포인트_결과와_리포트_점수가_저장된다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, B, C, A, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);
        Map<String, Boolean> verdicts = new LinkedHashMap<>();
        verdicts.put("READ_PATH_CACHE", true);
        verdicts.put("KEY_GENERATION_COLLISION", false);
        verdicts.put("STORAGE_ESTIMATION", true);
        verdicts.put("REDIRECT_AVAILABILITY", true);
        verdicts.put("REDIRECT_STATUS_TRADEOFF", false);

        // when
        answerService.saveLastAnswer(draft, endEvaluation(C, verdicts));

        // then
        assertAll(
                () -> assertThat(readEvaluationResults(systemDesignInterview)).containsExactly(
                        tuple("READ_PATH_CACHE", true),
                        tuple("KEY_GENERATION_COLLISION", false),
                        tuple("STORAGE_ESTIMATION", true),
                        tuple("REDIRECT_AVAILABILITY", true),
                        tuple("REDIRECT_STATUS_TRADEOFF", false)),
                // 100 × (0.5 × 3/5 + 0.5 × (0.75 + 0.75 + 0.5 + 1.0 + 0.75 + 0.5) / 6) = 65.4 → 65
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getReportScore()).isEqualTo(65)
        );
    }

    @Test
    void 끝나면_회원_점수에_등급_점수_합을_더한다() {
        // given
        Member member = saveMemberWithTokens(1, 100);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(A, A, B, B, C));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);

        // when
        answerService.saveLastAnswer(draft, endEvaluation(D, verdicts(2)));

        // then
        // A 20 + A 20 + B 10 + B 10 + C 0 + D -10 = 50
        assertAll(
                () -> assertThat(readInterview(systemDesignInterview).getTotalScore()).isEqualTo(50),
                () -> assertThat(readMemberScore(member)).isEqualTo(150)
        );
    }

    @Test
    void 끝난_면접은_요구사항_단계보다_먼저_끝남으로_거절한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview finishedInterview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(InterviewState.FINISHED)
                .build());
        SystemDesignInterview finishedInRequirements = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(finishedInterview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(SystemDesignStage.REQUIREMENTS)
                        .build());
        SystemDesignInterview finishedInDeepDive = saveInterviewWithAnswers(member, List.of(A, A, A, A, A, A));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> precheck(finishedInRequirements, member, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(INTERVIEW_FINISHED_MESSAGE),
                () -> assertThatThrownBy(() -> precheck(finishedInDeepDive, member, MAX_QUESTION_COUNT))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(INTERVIEW_FINISHED_MESSAGE),
                () -> assertThatThrownBy(() -> precheck(finishedInDeepDive, member, null))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(INTERVIEW_FINISHED_MESSAGE)
        );
    }

    @Test
    void 요구사항_단계에서는_요구사항_정리를_먼저_끝내라고_409다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
        SystemDesignInterview requirementsStage = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(SystemDesignStage.REQUIREMENTS)
                        .build());

        // when & then
        assertThatThrownBy(() -> precheck(requirementsStage, member, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("요구사항 정리를 먼저 끝내 주세요.");
    }

    @Test
    void 질문_순번이_null이거나_다르면_409다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> precheck(systemDesignInterview, member, null))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(NOT_CURRENT_QUESTION_MESSAGE),
                () -> assertThatThrownBy(() -> precheck(systemDesignInterview, member, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(NOT_CURRENT_QUESTION_MESSAGE),
                () -> assertThatThrownBy(() -> precheck(systemDesignInterview, member, 3))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(NOT_CURRENT_QUESTION_MESSAGE),
                // 질문 PK(순번 + 2)로는 답할 수 없다.
                () -> assertThatThrownBy(() -> precheck(systemDesignInterview, member,
                        Math.toIntExact(readQuestion(systemDesignInterview, 2).getId())))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage(NOT_CURRENT_QUESTION_MESSAGE),
                () -> assertThat(precheck(systemDesignInterview, member, 2)).isEqualTo(2)
        );
    }

    // 락 안에서 준비한 뒤 LLM을 부르는 동안 다른 요청이 같은 질문의 답을 저장한 경우(락 TTL이 지난 경우 등)다.
    @Test
    void 저장_직전에_이미_답이_있으면_실패한다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 1);
        answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(readQuestion(systemDesignInterview, 1))
                .content("먼저 저장된 답변이에요.")
                .build());

        // when & then
        assertThatThrownBy(() -> answerService.saveAnswer(draft, answerEvaluation(A, "다듬은 2번 질문이에요.")))
                .isInstanceOf(ConflictException.class)
                .hasMessage(NOT_CURRENT_QUESTION_MESSAGE);
        assertAll(
                () -> assertThat(readAnswers(systemDesignInterview))
                        .extracting(SystemDesignAnswer::getContent)
                        .containsExactly("먼저 저장된 답변이에요."),
                () -> assertThat(readQuestions(systemDesignInterview)).hasSize(1),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(6)
        );
    }

    @Test
    void 저장_직전에_면접이_끝났으면_마지막_답변을_저장하지_않는다() {
        // given
        Member member = saveMemberWithTokens(6, 100);
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(A, A, A, A, A));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);
        answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                .question(readQuestion(systemDesignInterview, MAX_QUESTION_COUNT))
                .content("먼저 저장된 마지막 답변이에요.")
                .build());
        Interview interview = readInterview(systemDesignInterview);
        interview.evaluate("먼저 저장된 종합 피드백이에요.", 100);
        interviewRepository.save(interview);

        // when & then
        assertThatThrownBy(() -> answerService.saveLastAnswer(draft, endEvaluation(A, verdicts(5))))
                .isInstanceOf(ConflictException.class)
                .hasMessage(INTERVIEW_FINISHED_MESSAGE);
        assertAll(
                () -> assertThat(readAnswers(systemDesignInterview)).hasSize(MAX_QUESTION_COUNT),
                () -> assertThat(readEvaluationResults(systemDesignInterview)).isEmpty(),
                () -> assertThat(readInterview(systemDesignInterview).getTotalFeedback())
                        .isEqualTo("먼저 저장된 종합 피드백이에요."),
                () -> assertThat(readMemberScore(member)).isEqualTo(100),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(6)
        );
    }

    @Test
    void 채점_입력에는_역질문과_이전_대화와_이번_답변과_다음_기준_질문이_들어간다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A));
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", List.of("DAILY_CREATION_VOLUME"));
        saveRequirementQuestion(systemDesignInterview, 2, "통계도 필요한가요?", List.of("CLICK_STATISTICS"));

        // when
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 3);

        // then
        SystemDesignAnswerCommand command = draft.answerCommand();
        assertAll(
                () -> assertThat(draft.isLast()).isFalse(),
                () -> assertThat(draft.endCommand()).isNull(),
                () -> assertThat(draft.interviewId()).isEqualTo(systemDesignInterview.getInterview().getId()),
                () -> assertThat(draft.memberId()).isEqualTo(member.getId()),
                () -> assertThat(draft.questionOrder()).isEqualTo(3),
                () -> assertThat(draft.content()).isEqualTo(ANSWER_CONTENT),
                () -> assertThat(draft.diagram()).isEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(command.interviewId()).isEqualTo(systemDesignInterview.getInterview().getId()),
                () -> assertThat(command.problem().title()).isEqualTo(SystemDesignProblemTestSupport.TITLE),
                () -> assertThat(command.problem().requirements())
                        .isEqualTo(SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS),
                () -> assertThat(command.problem().evaluationPoints())
                        .isEqualTo(SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS),
                () -> assertThat(command.requirementTurns()).containsExactly(
                        new SystemDesignRequirementTurn("하루에 몇 개나 만들어지나요?", "1번 역질문의 답이에요."),
                        new SystemDesignRequirementTurn("통계도 필요한가요?", "2번 역질문의 답이에요.")),
                () -> assertThat(command.previousTurns()).containsExactly(
                        new SystemDesignAnswerTurn(1, SystemDesignStage.HIGH_LEVEL,
                                readBaseQuestion(systemDesignInterview, 1), "1번 답변이에요."),
                        new SystemDesignAnswerTurn(2, SystemDesignStage.HIGH_LEVEL, "다듬은 2번 질문이에요.",
                                "2번 답변이에요.")),
                () -> assertThat(command.currentTurn()).isEqualTo(new SystemDesignAnswerTurn(3,
                        SystemDesignStage.HIGH_LEVEL, "다듬은 3번 질문이에요.", ANSWER_CONTENT)),
                () -> assertThat(command.currentDiagram()).isEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(command.nextBaseQuestion()).isEqualTo(new SystemDesignNextBaseQuestion(4,
                        SystemDesignStage.DEEP_DIVE, readBaseQuestion(systemDesignInterview, 4)))
        );
    }

    @Test
    void 마지막_질문의_채점_입력에는_확인한_요구사항_code가_중복_없이_들어간다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A, C, B, D));
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", List.of("DAILY_CREATION_VOLUME"));
        saveRequirementQuestion(systemDesignInterview, 2, "조회는 얼마나 되고 하루 몇 개인가요?",
                List.of("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"));
        saveRequirementQuestion(systemDesignInterview, 3, "인증은 필요한가요?", List.of());

        // when
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, MAX_QUESTION_COUNT);

        // then
        SystemDesignEndCommand command = draft.endCommand();
        assertAll(
                () -> assertThat(draft.isLast()).isTrue(),
                () -> assertThat(draft.answerCommand()).isNull(),
                () -> assertThat(command.confirmedRequirementCodes())
                        .containsExactly("DAILY_CREATION_VOLUME", "READ_WRITE_RATIO"),
                () -> assertThat(command.requirementTurns()).hasSize(3),
                () -> assertThat(command.previousTurns())
                        .extracting(SystemDesignAnswerTurn::questionOrder, SystemDesignAnswerTurn::stage)
                        .containsExactly(
                                tuple(1, SystemDesignStage.HIGH_LEVEL),
                                tuple(2, SystemDesignStage.HIGH_LEVEL),
                                tuple(3, SystemDesignStage.HIGH_LEVEL),
                                tuple(4, SystemDesignStage.DEEP_DIVE),
                                tuple(5, SystemDesignStage.DEEP_DIVE)),
                () -> assertThat(command.currentTurn()).isEqualTo(new SystemDesignAnswerTurn(MAX_QUESTION_COUNT,
                        SystemDesignStage.DEEP_DIVE, "다듬은 6번 질문이에요.", ANSWER_CONTENT)),
                () -> assertThat(command.currentDiagram()).isEqualTo(SUBMITTED_DIAGRAM)
        );
    }

    @Test
    void 답변이_있으면_Redis_값과_상관없이_COMPLETED다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();
        stateStore.markFailed(interviewId, 1);

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(), 1);

        // then
        assertThat(response).isEqualTo(new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED,
                InterviewState.IN_PROGRESS, SystemDesignStage.HIGH_LEVEL, B, 2, "다듬은 2번 질문이에요."));
    }

    @Test
    void 답변이_없고_LLM_PENDING이면_LLM_PENDING이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();
        stateStore.markPending(interviewId, 1);

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(), 1);

        // then
        assertThat(response).isEqualTo(new SystemDesignAnswerPendingResponse(InterviewProceedState.LLM_PENDING));
    }

    @Test
    void 답변이_없고_상태가_없으면_LLM_FAILED다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        SystemDesignAnswerResultResponse withoutState = answerService.findAnswerResult(interviewId, member.getId(), 1);
        stateStore.markFailed(interviewId, 1);
        SystemDesignAnswerResultResponse failed = answerService.findAnswerResult(interviewId, member.getId(), 1);

        // then
        assertAll(
                () -> assertThat(withoutState)
                        .isEqualTo(new SystemDesignAnswerPendingResponse(InterviewProceedState.LLM_FAILED)),
                () -> assertThat(failed)
                        .isEqualTo(new SystemDesignAnswerPendingResponse(InterviewProceedState.LLM_FAILED))
        );
    }

    // 워커는 저장을 커밋한 뒤 COMPLETED를 쓴다. 폴링이 답변을 읽은 뒤 Redis를 읽기 전에 워커가 끝나면 이 상태를 본다.
    // 실패로 보내면 저장된 답변을 다시 내게 되므로, 채점 중으로 보내 다음 폴링이 저장된 답변을 읽게 한다.
    @Test
    void 답변이_없는데_Redis가_COMPLETED면_LLM_PENDING이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of());
        Long interviewId = systemDesignInterview.getInterview().getId();
        stateStore.markCompleted(interviewId, 1);

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(), 1);

        // then
        assertThat(response).isEqualTo(new SystemDesignAnswerPendingResponse(InterviewProceedState.LLM_PENDING));
    }

    @Test
    void 지난_질문을_조회하면_그_답변을_처리한_결과를_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A, C));
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(), 1);

        // then
        // 면접의 지금 단계(DEEP_DIVE)가 아니라 그 답변 다음 질문(2번)의 단계다.
        assertAll(
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getStage())
                        .isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(response).isEqualTo(new SystemDesignAnswerCompletedResponse(
                        InterviewProceedState.COMPLETED, InterviewState.IN_PROGRESS, SystemDesignStage.HIGH_LEVEL, B,
                        2, "다듬은 2번 질문이에요."))
        );
    }

    @Test
    void 세_번째_답변의_결과는_DEEP_DIVE와_4번_질문이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A, C));
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(), 3);

        // then
        assertThat(response).isEqualTo(new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED,
                InterviewState.IN_PROGRESS, SystemDesignStage.DEEP_DIVE, C, 4, "다듬은 4번 질문이에요."));
    }

    @Test
    void 마지막_답변의_결과는_FINISHED이고_다음_질문이_null이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B, A, C, B, D, F));
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        SystemDesignAnswerResultResponse response = answerService.findAnswerResult(interviewId, member.getId(),
                MAX_QUESTION_COUNT);

        // then
        assertThat(response).isEqualTo(new SystemDesignAnswerCompletedResponse(InterviewProceedState.COMPLETED,
                InterviewState.FINISHED, SystemDesignStage.DEEP_DIVE, F, null, null));
    }

    @Test
    void 아직_없는_질문은_404다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B));
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> answerService.findAnswerResult(interviewId, member.getId(), 3))
                        .isInstanceOf(NotFoundException.class)
                        .hasMessage(QUESTION_NOT_FOUND_MESSAGE),
                () -> assertThatThrownBy(() -> answerService.findAnswerResult(interviewId, member.getId(), 0))
                        .isInstanceOf(NotFoundException.class)
                        .hasMessage(QUESTION_NOT_FOUND_MESSAGE),
                // 질문 2번의 PK는 4다. PK로 찾으면 다른 질문이 나온다.
                () -> assertThatThrownBy(() -> answerService.findAnswerResult(interviewId, member.getId(),
                        Math.toIntExact(readQuestion(systemDesignInterview, 2).getId())))
                        .isInstanceOf(NotFoundException.class)
                        .hasMessage(QUESTION_NOT_FOUND_MESSAGE)
        );
    }

    @Test
    void 질문_순번이_null이면_404다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveInterviewWithAnswers(member, List.of(B));

        // when & then
        assertThatThrownBy(() -> answerService.findAnswerResult(systemDesignInterview.getInterview().getId(),
                member.getId(), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(QUESTION_NOT_FOUND_MESSAGE);
    }

    private int precheck(SystemDesignInterview systemDesignInterview, Member member, Integer questionOrder) {
        return answerService.precheck(systemDesignInterview.getInterview().getId(), member.getId(), questionOrder);
    }

    private SystemDesignAnswerDraft prepare(SystemDesignInterview systemDesignInterview, Member member,
                                            int questionOrder) {
        return answerService.prepare(systemDesignInterview.getInterview().getId(), member.getId(), questionOrder,
                ANSWER_CONTENT, SUBMITTED_DIAGRAM);
    }

    private static SystemDesignAnswerEvaluation answerEvaluation(AnswerRank rank, String nextQuestion) {
        return new SystemDesignAnswerEvaluation(rank, rank + " 등급 피드백이에요.", nextQuestion);
    }

    private static SystemDesignEndEvaluation endEvaluation(AnswerRank rank, Map<String, Boolean> metByPointCode) {
        return new SystemDesignEndEvaluation(rank, rank + " 등급 피드백이에요.", TOTAL_FEEDBACK, metByPointCode);
    }

    // URL 단축기 문제의 평가 포인트 5개 중 앞에서부터 metCount개를 충족한 판정
    private static Map<String, Boolean> verdicts(int metCount) {
        List<EvaluationPoint> points = SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS;
        Map<String, Boolean> metByPointCode = new LinkedHashMap<>();
        for (int index = 0; index < points.size(); index++) {
            metByPointCode.put(points.get(index).code(), index < metCount);
        }
        return metByPointCode;
    }

    /**
     * 앞의 질문 ranks.size()개에 답이 저장된 면접. 6개 모두 답했으면 끝난 면접이고, 아니면 그다음 질문이 현재 질문이다.
     * 1번 질문은 기준 질문 그대로이고 2번부터는 "다듬은 n번 질문이에요."다. 답변은 "n번 답변이에요."다.
     */
    private SystemDesignInterview saveInterviewWithAnswers(Member member, List<AnswerRank> ranks) {
        int answeredCount = ranks.size();
        boolean finished = answeredCount == MAX_QUESTION_COUNT;
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(finished ? InterviewState.FINISHED : InterviewState.IN_PROGRESS)
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(finished ? SystemDesignStage.DEEP_DIVE
                                : SystemDesignStage.ofQuestionOrder(answeredCount + 1))
                        .build());
        int questionCount = finished ? MAX_QUESTION_COUNT : answeredCount + 1;
        for (int order = 1; order <= questionCount; order++) {
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order);
            if (order <= answeredCount) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                        .question(question)
                        .content(order + "번 답변이에요.")
                        .answerRank(ranks.get(order - 1))
                        .build());
            }
        }
        return systemDesignInterview;
    }

    private SystemDesignQuestion saveQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.findByProblemAndQuestionOrder(
                systemDesignInterview.getProblem(), questionOrder).orElseThrow();
        String content = questionOrder == 1 ? problemQuestion.getContent() : "다듬은 " + questionOrder + "번 질문이에요.";
        return questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(problemQuestion)
                .content(content)
                .build());
    }

    private void saveRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder,
                                         String content, List<String> askedRequirementCodes) {
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .content(content)
                .answer(questionOrder + "번 역질문의 답이에요.")
                .askedRequirementCodes(askedRequirementCodes)
                .build());
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        return saveMemberWithTokens(freeTokenCount, 0);
    }

    private Member saveMemberWithTokens(int freeTokenCount, int score) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().score(score).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private String readBaseQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return problemQuestionRepository.findByProblemAndQuestionOrder(systemDesignInterview.getProblem(),
                questionOrder).orElseThrow().getContent();
    }

    private List<SystemDesignQuestion> readQuestions(SystemDesignInterview systemDesignInterview) {
        return questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    private SystemDesignQuestion readQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return questionRepository.findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, questionOrder)
                .orElseThrow();
    }

    private List<SystemDesignAnswer> readAnswers(SystemDesignInterview systemDesignInterview) {
        return answerRepository.findByQuestionIn(readQuestions(systemDesignInterview));
    }

    private SystemDesignAnswer readAnswer(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return answerRepository.findByQuestion(readQuestion(systemDesignInterview, questionOrder)).orElseThrow();
    }

    private SystemDesignInterview readSystemDesignInterview(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterviewRepository.findById(systemDesignInterview.getId()).orElseThrow();
    }

    private Interview readInterview(SystemDesignInterview systemDesignInterview) {
        return interviewRepository.findById(systemDesignInterview.getInterview().getId()).orElseThrow();
    }

    // 평가 포인트 code는 지연 로딩이라 트랜잭션 안에서 읽는다. 저장한 순서(문제의 평가 포인트 순서)대로 준다.
    private List<Tuple> readEvaluationResults(SystemDesignInterview systemDesignInterview) {
        return transactionTemplate.execute(status -> evaluationResultRepository
                .findBySystemDesignInterview(systemDesignInterview).stream()
                .sorted(Comparator.comparing(SystemDesignEvaluationResult::getId))
                .map(result -> tuple(result.getEvaluationPoint().getCode(), result.isMet()))
                .toList());
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }

    private int readMemberScore(Member member) {
        return memberRepository.findById(member.getId()).orElseThrow().getScore();
    }

    private TokenPurchaseRequest createTokenPurchaseRequest() {
        return new TokenPurchaseRequest("payment_key", "order_id", TokenProduct.TOKEN_10.getPrice(),
                TokenProduct.TOKEN_10.getOrderName(), TokenProduct.TOKEN_10.name());
    }

    private TosspaymentsPaymentResponse createConfirmedTokenPaymentResponse() {
        return new TosspaymentsPaymentResponse(
                "payment_key", PaymentType.NORMAL, "order_id", TokenProduct.TOKEN_10.getOrderName(),
                "tvivarepublica", "KRW", "카드", TokenProduct.TOKEN_10.getPrice(), TokenProduct.TOKEN_10.getPrice(),
                TosspaymentsStatus.DONE, LocalDateTime.now(), LocalDateTime.now(),
                "transaction_key", 9091L, 909L, 0L, 0L, true,
                "{}", null, null, null, "KR", null, null
        );
    }
}
