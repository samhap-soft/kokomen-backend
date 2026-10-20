package com.samhap.kokomen.interview.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.tool.InterviewProceedResult;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 답변 토큰 차감(REQUIRES_NEW)은 호출 즉시 따로 커밋되어 진행 트랜잭션이 롤백돼도 돌아오지 않는다.
 * 그래서 응답 해석이나 저장이 실패한 진행에는 토큰이 그대로 남아 있어야 한다.
 */
class InterviewProceedServiceTest extends BaseTest {

    private static final int INITIAL_FREE_TOKEN_COUNT = 20;
    private static final int TOTAL_FEEDBACK_MAX_LENGTH = 2_000;
    // TokenFacadeService의 @DistributedLock(prefix = "token", key = "#memberId")가 쓰는 Redisson 락 이름
    private static final String TOKEN_LOCK_KEY_PREFIX = "lock:token:";
    private static final Duration CONCURRENT_TASK_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private InterviewProceedService interviewProceedService;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;

    @Test
    void 면접을_이어가면_다음_질문과_답변을_저장하고_답변_토큰_1개를_차감한다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, 1);
        BedrockConverseResponse response = BedrockResponseFixtureBuilder.builder()
                .nextQuestion("데드락이 발생하는 조건은 무엇인가요?")
                .buildProceed();

        // when
        InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                member.getId(), createQuestionAndAnswers(interview, curQuestion), response, interview.getId());

        // then
        assertThat(result.isInProgress()).isTrue();
        assertThat(result.getNextQuestion().getContent()).isEqualTo("데드락이 발생하는 조건은 무엇인가요?");
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isPresent();
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT - 1);
    }

    @Test
    void 마지막_답변이면_면접을_끝내고_답변_토큰_1개를_차감한다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);

        // when
        InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                member.getId(), createQuestionAndAnswers(interview, curQuestion),
                BedrockResponseFixtureBuilder.builder().buildEnd(), interview.getId());

        // then
        assertThat(result.isInProgress()).isFalse();
        assertThat(readInterview(interview).getInterviewState()).isEqualTo(InterviewState.FINISHED);
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT - 1);
    }

    @Test
    void 마지막_답변이면_면접_총점을_회원_점수에_더한다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);

        // when
        interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                createQuestionAndAnswers(interview, curQuestion), BedrockResponseFixtureBuilder.builder().buildEnd(),
                interview.getId());

        // then
        Integer totalScore = readInterview(interview).getTotalScore();
        assertThat(totalScore).isPositive();
        assertThat(readMemberScore(member)).isEqualTo(totalScore);
    }

    /**
     * 차감은 면접 평가 UPDATE를 flush한 뒤 별도 트랜잭션에서 실행되므로, 그동안 진행 트랜잭션이 면접·답변 행 락을 쥐고 있다.
     * 유료 토큰 차감은 token과 token_purchase를 함께 갱신하는데, 이 락에 막히지 않고 끝나야 한다.
     */
    @Test
    void 마지막_답변을_유료_토큰으로_차감해도_면접을_끝낸다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(0).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(1).build());
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                .memberId(member.getId()).purchaseCount(1).state(TokenPurchaseState.USABLE).build());
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);

        // when
        InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                member.getId(), createQuestionAndAnswers(interview, curQuestion),
                BedrockResponseFixtureBuilder.builder().buildEnd(), interview.getId());

        // then
        assertThat(result.isInProgress()).isFalse();
        assertThat(readInterview(interview).getInterviewState()).isEqualTo(InterviewState.FINISHED);
        assertThat(tokenRepository.findByMemberIdAndType(member.getId(), TokenType.PAID).orElseThrow()
                .getTokenCount()).isZero();
        assertThat(tokenPurchaseRepository.findById(tokenPurchase.getId()).orElseThrow().getRemainingCount())
                .isZero();
    }

    /**
     * 토큰 구매는 토큰 락을 쥔 채 결제 승인을 기다리고, 승인 뒤 token_purchase INSERT의 FK 검사로 회원 행 S 락을 잡는다.
     * 마지막 답변이 회원 점수 UPDATE로 회원 행 X 락을 쥔 채 차감용 토큰 락을 기다리면 두 쪽이 서로를 기다린다.
     * DB는 Redis 락 대기를 모르므로 이 순환은 토큰 락 대기 시간(3초)이 지나야 풀리고, 그때 답변이 실패해 면접이 끝나지 않는다.
     */
    @Test
    void 토큰_구매_승인_중에_마지막_답변을_차감해도_면접을_끝낸다() throws InterruptedException {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);
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
            Future<InterviewProceedResult> proceed = executorService.submit(
                    () -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                            questionAndAnswers, BedrockResponseFixtureBuilder.builder().buildEnd(), interview.getId()));
            // 차감이 토큰 락을 요청한 뒤에 결제 승인을 끝내, 차감 전에 실행된 쓰기가 구매의 INSERT보다 먼저 락을 잡게 한다.
            verify(redissonClient, timeout(CONCURRENT_TASK_TIMEOUT.toMillis()))
                    .getLock(TOKEN_LOCK_KEY_PREFIX + member.getId());
            confirmReleased.countDown();

            // then
            assertThat(purchase).succeedsWithin(CONCURRENT_TASK_TIMEOUT);
            assertThat(proceed).succeedsWithin(CONCURRENT_TASK_TIMEOUT)
                    .extracting(InterviewProceedResult::isInProgress)
                    .isEqualTo(false);
        } finally {
            // 단언이 중간에 실패해도 멈춰 둔 결제 승인을 풀고 두 작업이 끝나기를 기다려, 다음 테스트에 쓰기가 남지 않게 한다.
            confirmReleased.countDown();
            executorService.shutdown();
            if (!executorService.awaitTermination(CONCURRENT_TASK_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
        Interview finishedInterview = readInterview(interview);
        assertThat(finishedInterview.getInterviewState()).isEqualTo(InterviewState.FINISHED);
        assertThat(readMemberScore(member)).isEqualTo(finishedInterview.getTotalScore());
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT - 1);
        assertThat(readPaidTokenCount(member)).isEqualTo(TokenProduct.TOKEN_10.getTokenCount());
    }

    @Test
    void 다음_질문을_해석하지_못하면_답변_토큰을_차감하지_않는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);

        // when & then
        assertThatThrownBy(() -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                questionAndAnswers, BedrockResponseFixtureBuilder.builder().buildProceedWithObjectNextQuestion(),
                interview.getId()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("Bedrock toolUse 파싱 실패: type=NextQuestionResponse");
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT);
        assertThat(questionRepository.countByInterview(interview)).isEqualTo(2);
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isEmpty();
    }

    @Test
    void 종합_피드백이_비어_면접을_끝내지_못하면_답변_토큰을_차감하지_않는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);
        BedrockConverseResponse blankTotalFeedback = BedrockResponseFixtureBuilder.builder()
                .strengths(" ")
                .improvements(" ")
                .learningDirection(" ")
                .buildEnd();

        // when & then
        assertThatThrownBy(() -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                questionAndAnswers, blankTotalFeedback, interview.getId()))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("면접 종합 피드백 응답이 비어있습니다.");
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT);
        assertThat(readInterview(interview).getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS);
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isEmpty();
    }

    /**
     * 면접 평가는 엔티티 변경으로 기록되어 UPDATE가 커밋 때로 미뤄진다. 차감이 그보다 먼저 커밋되면
     * 저장에 실패한 진행에도 과금된다. 종합 피드백 컬럼(VARCHAR 2000)을 넘겨 그 UPDATE를 실패시킨다.
     */
    @Test
    void 면접_평가를_저장하지_못하면_답변_토큰을_차감하지_않는다() {
        // given
        Member member = saveMemberWithTokens(INITIAL_FREE_TOKEN_COUNT);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);
        BedrockConverseResponse tooLongTotalFeedback = BedrockResponseFixtureBuilder.builder()
                .strengths("가".repeat(TOTAL_FEEDBACK_MAX_LENGTH + 1))
                .buildEnd();

        // when & then
        assertThatThrownBy(() -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                questionAndAnswers, tooLongTotalFeedback, interview.getId()))
                .hasStackTraceContaining("total_feedback");
        assertThat(readFreeTokenCount(member)).isEqualTo(INITIAL_FREE_TOKEN_COUNT);
        assertThat(readInterview(interview).getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS);
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isEmpty();
    }

    @Test
    void 토큰이_없으면_진행_결과를_저장하지_않는다() {
        // given
        Member member = saveMemberWithTokens(0);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);

        // when & then
        assertThatThrownBy(() -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                questionAndAnswers, BedrockResponseFixtureBuilder.builder().buildProceed(), interview.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(questionRepository.countByInterview(interview)).isEqualTo(2);
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isEmpty();
    }

    @Test
    void 마지막_답변에_쓸_토큰이_없으면_면접을_끝내지_않고_회원_점수도_더하지_않는다() {
        // given
        Member member = saveMemberWithTokens(0);
        Interview interview = saveInterview(member);
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(interview, curQuestion);

        // when & then
        assertThatThrownBy(() -> interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(member.getId(),
                questionAndAnswers, BedrockResponseFixtureBuilder.builder().buildEnd(), interview.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(readInterview(interview).getInterviewState()).isEqualTo(InterviewState.IN_PROGRESS);
        assertThat(readMemberScore(member)).isZero();
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isEmpty();
    }

    // 비회원은 차감할 토큰도 점수를 더할 회원도 없다. 회원 전용 단계를 건너뛰고 진행 결과는 그대로 저장되는지 고정한다.
    @Test
    void 비회원_면접을_이어가면_다음_질문과_답변을_저장한다() {
        // given
        Interview interview = saveGuestInterview();
        Question curQuestion = saveCurrentQuestion(interview, 1);

        // when
        InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                null, createQuestionAndAnswers(interview, curQuestion),
                BedrockResponseFixtureBuilder.builder().buildProceed(), interview.getId());

        // then
        assertThat(result.isInProgress()).isTrue();
        assertThat(questionRepository.countByInterview(interview)).isEqualTo(3);
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isPresent();
    }

    @Test
    void 비회원_면접의_마지막_답변이면_면접을_끝낸다() {
        // given
        Interview interview = saveGuestInterview();
        Question curQuestion = saveCurrentQuestion(interview, interview.getMaxQuestionCount() - 1);

        // when
        InterviewProceedResult result = interviewProceedService.proceedOrEndInterviewByBedrockFlowAsync(
                null, createQuestionAndAnswers(interview, curQuestion),
                BedrockResponseFixtureBuilder.builder().buildEnd(), interview.getId());

        // then
        assertThat(result.isInProgress()).isFalse();
        Interview finishedInterview = readInterview(interview);
        assertThat(finishedInterview.getInterviewState()).isEqualTo(InterviewState.FINISHED);
        assertThat(finishedInterview.getTotalScore()).isNotNull();
        assertThat(answerRepository.findByQuestionId(curQuestion.getId())).isPresent();
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private Interview saveInterview(Member member) {
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        return interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .build());
    }

    private Interview saveGuestInterview() {
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        return interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(null)
                .guestIp("1.2.3.4")
                .rootQuestion(rootQuestion)
                .build());
    }

    // 앞의 질문 answeredQuestionCount개에 이미 답했고, 그다음 질문이 현재 질문인 상태를 만든다.
    private Question saveCurrentQuestion(Interview interview, int answeredQuestionCount) {
        for (int i = 0; i < answeredQuestionCount; i++) {
            Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
            answerRepository.save(AnswerFixtureBuilder.builder().question(question).answerRank(AnswerRank.B).build());
        }
        return questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
    }

    private QuestionAndAnswers createQuestionAndAnswers(Interview interview, Question curQuestion) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, "답변입니다.", curQuestion.getId(), interview);
    }

    private Interview readInterview(Interview interview) {
        return interviewRepository.findById(interview.getId()).orElseThrow();
    }

    private int readFreeTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow().getTokenCount();
    }

    private int readPaidTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.PAID).orElseThrow().getTokenCount();
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
