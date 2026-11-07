package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequestV2;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시작 검증, 답변 전 검증, 실제 차감이 같은 토큰 규칙을 따르는지 고정한다. 답변마다 1개를 차감하고, 음성 모드에서는
 * 다음 질문 음성마다 1개를 더 차감하며, 첫 질문 음성은 차감하지 않는다. 그래서 3문항 면접은 텍스트 3개, 음성 5개를 쓴다.
 * 경계 테스트는 정확히 필요한 만큼은 통과하고 하나 모자라면 거절되는지를 함께 본다.
 */
class InterviewTokenChargeTest extends BaseTest {

    private static final int MAX_QUESTION_COUNT = 3;
    private static final Duration ASYNC_PROCEED_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ASYNC_PROCEED_POLL_INTERVAL = Duration.ofMillis(20);

    @Autowired
    private InterviewStartFacadeService interviewStartFacadeService;
    @Autowired
    private InterviewProceedFacadeService interviewProceedFacadeService;
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
    private RedisService redisService;

    @Test
    void 텍스트_카테고리_면접은_최대_질문_수만큼_토큰이_있어야_시작할_수_있다() {
        // given
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        Member enough = saveMemberWithTokens(3);
        Member notEnough = saveMemberWithTokens(2);
        InterviewRequest request = new InterviewRequest(Category.OPERATING_SYSTEM, MAX_QUESTION_COUNT,
                InterviewMode.TEXT, false);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startInterview(request,
                new MemberAuth(enough.getId()));

        // then
        assertThat(interviewRepository.findById(response.interviewId())).isPresent();
        assertThatThrownBy(() -> interviewStartFacadeService.startInterview(request,
                new MemberAuth(notEnough.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(interviewRepository.count()).isEqualTo(1);
    }

    @Test
    void 음성_카테고리_면접은_최대_질문_수의_2배에서_1을_뺀_만큼_토큰이_있어야_시작할_수_있다() {
        // given
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        Member enough = saveMemberWithTokens(5);
        Member notEnough = saveMemberWithTokens(4);
        InterviewRequest request = new InterviewRequest(Category.OPERATING_SYSTEM, MAX_QUESTION_COUNT,
                InterviewMode.VOICE, false);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startInterview(request,
                new MemberAuth(enough.getId()));

        // then
        assertThat(interviewRepository.findById(response.interviewId())).isPresent();
        assertThatThrownBy(() -> interviewStartFacadeService.startInterview(request,
                new MemberAuth(notEnough.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(interviewRepository.count()).isEqualTo(1);
    }

    @Test
    void 텍스트_커스텀_면접은_최대_질문_수만큼_토큰이_있어야_시작할_수_있다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member enough = saveMemberWithTokens(3);
        Member notEnough = saveMemberWithTokens(2);
        RootQuestionCustomInterviewRequest request = new RootQuestionCustomInterviewRequest(rootQuestion.getId(),
                MAX_QUESTION_COUNT, InterviewMode.TEXT);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startRootQuestionCustomInterview(request,
                new MemberAuth(enough.getId()));

        // then
        assertThat(interviewRepository.findById(response.interviewId())).isPresent();
        assertThatThrownBy(() -> interviewStartFacadeService.startRootQuestionCustomInterview(request,
                new MemberAuth(notEnough.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(interviewRepository.count()).isEqualTo(1);
    }

    @Test
    void 음성_커스텀_면접은_최대_질문_수의_2배에서_1을_뺀_만큼_토큰이_있어야_시작할_수_있다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member enough = saveMemberWithTokens(5);
        Member notEnough = saveMemberWithTokens(4);
        RootQuestionCustomInterviewRequest request = new RootQuestionCustomInterviewRequest(rootQuestion.getId(),
                MAX_QUESTION_COUNT, InterviewMode.VOICE);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startRootQuestionCustomInterview(request,
                new MemberAuth(enough.getId()));

        // then
        assertThat(interviewRepository.findById(response.interviewId())).isPresent();
        assertThatThrownBy(() -> interviewStartFacadeService.startRootQuestionCustomInterview(request,
                new MemberAuth(notEnough.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(interviewRepository.count()).isEqualTo(1);
    }

    @Test
    void 음성_면접에서_다음_질문이_이어지는_답변은_토큰이_2개_있어야_제출할_수_있다() throws InterruptedException {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildProceed());
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        given(answerFeedbackBedrockClient.requestAnswerFeedback(any(), any())).willReturn("답변 피드백입니다.");
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member enough = saveMemberWithTokens(2);
        Member notEnough = saveMemberWithTokens(1);
        Question enoughQuestion = saveCurrentQuestionOfVoiceInterview(enough, rootQuestion, 1);
        Question notEnoughQuestion = saveCurrentQuestionOfVoiceInterview(notEnough, rootQuestion, 1);

        // when
        InterviewProceedState proceedState = submitVoiceAnswerAndAwait(enough, enoughQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        assertThat(readFreeTokenCount(enough)).isZero();
        assertThatThrownBy(() -> submitVoiceAnswer(notEnough, notEnoughQuestion))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
        assertThat(readFreeTokenCount(notEnough)).isEqualTo(1);
    }

    @Test
    void 음성_면접의_마지막_답변은_다음_질문_음성이_없으므로_토큰_1개로_제출할_수_있다() throws InterruptedException {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildEnd());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member enough = saveMemberWithTokens(1);
        Member notEnough = saveMemberWithTokens(0);
        Question enoughQuestion = saveCurrentQuestionOfVoiceInterview(enough, rootQuestion, MAX_QUESTION_COUNT - 1);
        Question notEnoughQuestion = saveCurrentQuestionOfVoiceInterview(notEnough, rootQuestion,
                MAX_QUESTION_COUNT - 1);

        // when
        InterviewProceedState proceedState = submitVoiceAnswerAndAwait(enough, enoughQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        assertThat(readInterviewState(enoughQuestion.getInterview().getId())).isEqualTo(InterviewState.FINISHED);
        assertThat(readFreeTokenCount(enough)).isZero();
        assertThatThrownBy(() -> submitVoiceAnswer(notEnough, notEnoughQuestion))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
    }

    /**
     * 시작 검증·답변 전 검증·실제 차감 중 하나라도 어긋나면 이 흐름이 깨진다. 시작 요구량이 실제 차감보다 적거나
     * 답변 전 검증이 실제 차감보다 많으면 중간 답변이 거절되고, 시작 요구량이 실제 차감보다 많으면 토큰이 남는다.
     */
    @Test
    void 음성_면접은_시작할_때_요구한_토큰만으로_마지막_답변까지_진행되고_토큰이_남지_않는다() throws InterruptedException {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any())).willReturn(
                BedrockResponseFixtureBuilder.builder().buildProceed(),
                BedrockResponseFixtureBuilder.builder().buildProceed(),
                BedrockResponseFixtureBuilder.builder().buildEnd());
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        given(answerFeedbackBedrockClient.requestAnswerFeedback(any(), any())).willReturn("답변 피드백입니다.");
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        Member member = saveMemberWithTokens(5);
        InterviewStartResponse response = interviewStartFacadeService.startInterview(
                new InterviewRequest(Category.OPERATING_SYSTEM, MAX_QUESTION_COUNT, InterviewMode.VOICE, false),
                new MemberAuth(member.getId()));
        Long interviewId = response.interviewId();

        // when
        InterviewProceedState firstState = submitVoiceAnswerAndAwait(member, interviewId, response.questionId());
        InterviewProceedState secondState = submitVoiceAnswerAndAwait(member, interviewId,
                readLatestQuestionId(interviewId));
        InterviewProceedState lastState = submitVoiceAnswerAndAwait(member, interviewId,
                readLatestQuestionId(interviewId));

        // then
        assertThat(List.of(firstState, secondState, lastState)).containsOnly(InterviewProceedState.COMPLETED);
        assertThat(readInterviewState(interviewId)).isEqualTo(InterviewState.FINISHED);
        assertThat(readFreeTokenCount(member)).isZero();
    }

    /**
     * 음성 면접과 같은 흐름을 텍스트 면접에서도 실제 차감으로 고정한다. 텍스트 면접은 질문 음성이 없어 답변마다 1개만 쓴다.
     */
    @Test
    void 텍스트_면접은_시작할_때_요구한_토큰만으로_마지막_답변까지_진행되고_토큰이_남지_않는다() throws InterruptedException {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any())).willReturn(
                BedrockResponseFixtureBuilder.builder().buildProceed(),
                BedrockResponseFixtureBuilder.builder().buildProceed(),
                BedrockResponseFixtureBuilder.builder().buildEnd());
        given(answerFeedbackBedrockClient.requestAnswerFeedback(any(), any())).willReturn("답변 피드백입니다.");
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        Member member = saveMemberWithTokens(3);
        InterviewStartResponse response = interviewStartFacadeService.startInterview(
                new InterviewRequest(Category.OPERATING_SYSTEM, MAX_QUESTION_COUNT, InterviewMode.TEXT, false),
                new MemberAuth(member.getId()));
        Long interviewId = response.interviewId();

        // when
        InterviewProceedState firstState = submitAnswerAndAwait(member, interviewId, response.questionId(),
                InterviewMode.TEXT);
        InterviewProceedState secondState = submitAnswerAndAwait(member, interviewId,
                readLatestQuestionId(interviewId), InterviewMode.TEXT);
        InterviewProceedState lastState = submitAnswerAndAwait(member, interviewId,
                readLatestQuestionId(interviewId), InterviewMode.TEXT);

        // then
        assertThat(List.of(firstState, secondState, lastState)).containsOnly(InterviewProceedState.COMPLETED);
        assertThat(readInterviewState(interviewId)).isEqualTo(InterviewState.FINISHED);
        assertThat(readFreeTokenCount(member)).isZero();
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    // 최대 3문항 음성 면접에서 앞의 질문 answeredQuestionCount개에 이미 답했고, 그다음 질문이 현재 질문인 상태를 만든다.
    private Question saveCurrentQuestionOfVoiceInterview(Member member, RootQuestion rootQuestion,
                                                         int answeredQuestionCount) {
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .maxQuestionCount(MAX_QUESTION_COUNT)
                .interviewMode(InterviewMode.VOICE)
                .build());
        for (int i = 0; i < answeredQuestionCount; i++) {
            Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
            answerRepository.save(AnswerFixtureBuilder.builder().question(question).answerRank(AnswerRank.B).build());
        }
        return questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
    }

    private void submitVoiceAnswer(Member member, Question curQuestion) {
        interviewProceedFacadeService.proceedInterviewByBedrockFlow(curQuestion.getInterview().getId(),
                curQuestion.getId(), new AnswerRequestV2("답변입니다.", InterviewMode.VOICE),
                new MemberAuth(member.getId()), new ClientIp("0.0.0.0"));
    }

    private InterviewProceedState submitVoiceAnswerAndAwait(Member member, Question curQuestion)
            throws InterruptedException {
        return submitVoiceAnswerAndAwait(member, curQuestion.getInterview().getId(), curQuestion.getId());
    }

    private InterviewProceedState submitVoiceAnswerAndAwait(Member member, Long interviewId, Long curQuestionId)
            throws InterruptedException {
        return submitAnswerAndAwait(member, interviewId, curQuestionId, InterviewMode.VOICE);
    }

    private InterviewProceedState submitAnswerAndAwait(Member member, Long interviewId, Long curQuestionId,
                                                       InterviewMode mode) throws InterruptedException {
        interviewProceedFacadeService.proceedInterviewByBedrockFlow(interviewId, curQuestionId,
                new AnswerRequestV2("답변입니다.", mode), new MemberAuth(member.getId()),
                new ClientIp("0.0.0.0"));
        awaitProceedLockReleased(member);
        String proceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interviewId,
                curQuestionId);
        return redisService.get(proceedStateKey, String.class)
                .map(InterviewProceedState::valueOf)
                .orElseThrow();
    }

    // 비동기 진행 태스크는 성공·실패 어느 쪽이든 마지막에 진행 락을 해제하므로, 락이 사라지면 진행 결과가 확정된 것이다.
    private void awaitProceedLockReleased(Member member) throws InterruptedException {
        String lockKey = InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
        long deadline = System.nanoTime() + ASYNC_PROCEED_TIMEOUT.toNanos();
        while (redisService.get(lockKey, String.class).isPresent()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("비동기 답변 처리가 제한 시간 안에 끝나지 않았다. lockKey=" + lockKey);
            }
            Thread.sleep(ASYNC_PROCEED_POLL_INTERVAL.toMillis());
        }
    }

    private Long readLatestQuestionId(Long interviewId) {
        return questionRepository.findTop2ByInterviewIdOrderByIdDesc(interviewId).get(0).getId();
    }

    private InterviewState readInterviewState(Long interviewId) {
        return interviewRepository.findById(interviewId).orElseThrow().getInterviewState();
    }

    private int readFreeTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow().getTokenCount();
    }
}
