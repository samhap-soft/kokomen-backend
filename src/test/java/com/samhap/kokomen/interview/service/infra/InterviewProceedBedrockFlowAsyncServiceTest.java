package com.samhap.kokomen.interview.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
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
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.interview.service.core.InterviewProceedService;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class InterviewProceedBedrockFlowAsyncServiceTest extends BaseTest {

    private static final int MAX_QUESTION_COUNT = 3;

    @Autowired
    private InterviewProceedService interviewProceedService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private RedisService redisService;
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

    private InterviewProceedBedrockFlowAsyncService asyncService;

    /**
     * 제출한 태스크를 호출 스레드에서 바로 실행하는 executor로 서비스를 수동 조립한다. 메서드가 반환되면 진행 상태가
     * 확정되므로 비동기 완료를 기다리지 않는다. Bedrock 클라이언트는 BaseTest의 목을 그대로 넘기고, 음성 생성은
     * 실제 QuestionService가 BaseTest의 SupertoneClient·S3Client 목을 거쳐 수행한다.
     */
    @BeforeEach
    void setUpAsyncService() {
        asyncService = new InterviewProceedBedrockFlowAsyncService(interviewProceedService, questionService,
                tokenFacadeService, interviewProceedBedrockClient, answerFeedbackBedrockClient, redisService,
                new CallerRunsTaskExecutor());
        given(answerFeedbackBedrockClient.requestAnswerFeedback(any(), any())).willReturn("답변 피드백입니다.");
    }

    @Test
    void 음성_생성_뒤_음성_토큰_차감에_실패해도_TTS_FAILED로_기록하지_않는다() {
        // given - 답변 토큰을 차감하면 음성 토큰을 차감할 토큰이 남지 않는다
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildProceed());
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        Member member = saveMemberWithTokens(1);
        Question curQuestion = saveCurrentQuestion(member, InterviewMode.VOICE, 1);

        // when
        InterviewProceedState proceedState = proceed(member, curQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        verify(supertoneClient, times(1)).request(any());
        assertThat(questionRepository.countByInterview(curQuestion.getInterview())).isEqualTo(3);
        assertThat(readFreeTokenCount(member)).isZero();
    }

    @Test
    void 음성_생성에_실패하면_TTS_FAILED로_기록하고_음성_토큰을_차감하지_않는다() {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildProceed());
        given(supertoneClient.request(any())).willThrow(new RuntimeException("음성 생성 실패"));
        Member member = saveMemberWithTokens(2);
        Question curQuestion = saveCurrentQuestion(member, InterviewMode.VOICE, 1);

        // when
        InterviewProceedState proceedState = proceed(member, curQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.TTS_FAILED);
        assertThat(readFreeTokenCount(member)).isEqualTo(1);
    }

    @Test
    void 음성_면접에서_다음_질문이_이어지면_답변_전에_검증한_만큼_차감한다() {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildProceed());
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        Member member = saveMemberWithTokens(InterviewMode.VOICE.calculateRequiredTokenCountToAnswer(true));
        Question curQuestion = saveCurrentQuestion(member, InterviewMode.VOICE, 1);

        // when
        InterviewProceedState proceedState = proceed(member, curQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        verify(supertoneClient, times(1)).request(any());
        assertThat(readFreeTokenCount(member)).isZero();
    }

    @Test
    void 음성_면접의_마지막_답변은_음성을_만들지_않고_답변_전에_검증한_만큼_차감한다() {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildEnd());
        Member member = saveMemberWithTokens(InterviewMode.VOICE.calculateRequiredTokenCountToAnswer(false));
        Question curQuestion = saveCurrentQuestion(member, InterviewMode.VOICE, MAX_QUESTION_COUNT - 1);

        // when
        InterviewProceedState proceedState = proceed(member, curQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        verify(supertoneClient, never()).request(any());
        assertThat(interviewRepository.findById(curQuestion.getInterview().getId()).orElseThrow()
                .getInterviewState()).isEqualTo(InterviewState.FINISHED);
        assertThat(readFreeTokenCount(member)).isZero();
    }

    @Test
    void 텍스트_면접은_음성을_만들지_않고_답변_전에_검증한_만큼_차감한다() {
        // given
        given(interviewProceedBedrockClient.requestToBedrock(any()))
                .willReturn(BedrockResponseFixtureBuilder.builder().buildProceed());
        Member member = saveMemberWithTokens(InterviewMode.TEXT.calculateRequiredTokenCountToAnswer(true));
        Question curQuestion = saveCurrentQuestion(member, InterviewMode.TEXT, 1);

        // when
        InterviewProceedState proceedState = proceed(member, curQuestion);

        // then
        assertThat(proceedState).isEqualTo(InterviewProceedState.COMPLETED);
        verify(supertoneClient, never()).request(any());
        assertThat(readFreeTokenCount(member)).isZero();
    }

    private InterviewProceedState proceed(Member member, Question curQuestion) {
        Interview interview = curQuestion.getInterview();
        asyncService.proceedInterviewByBedrockFlowAsync(member.getId(),
                createQuestionAndAnswers(interview, curQuestion), interview.getId(),
                InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId()),
                UUID.randomUUID().toString());
        String proceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interview.getId(),
                curQuestion.getId());
        return redisService.get(proceedStateKey, String.class)
                .map(InterviewProceedState::valueOf)
                .orElseThrow();
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    // 최대 3문항 면접에서 앞의 질문 answeredQuestionCount개에 이미 답했고, 그다음 질문이 현재 질문인 상태를 만든다.
    private Question saveCurrentQuestion(Member member, InterviewMode interviewMode, int answeredQuestionCount) {
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .maxQuestionCount(MAX_QUESTION_COUNT)
                .interviewMode(interviewMode)
                .build());
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

    private int readFreeTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow().getTokenCount();
    }

    private static class CallerRunsTaskExecutor extends ThreadPoolTaskExecutor {

        @Override
        public void execute(Runnable task) {
            task.run();
        }
    }
}
