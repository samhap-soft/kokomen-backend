package com.samhap.kokomen.interview.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 답변 토큰 차감(REQUIRES_NEW)은 호출 즉시 따로 커밋되어 진행 트랜잭션이 롤백돼도 돌아오지 않는다.
 * 그래서 응답 해석이나 저장이 실패한 진행에는 토큰이 그대로 남아 있어야 한다.
 */
class InterviewProceedServiceTest extends BaseTest {

    private static final int INITIAL_FREE_TOKEN_COUNT = 20;
    private static final int TOTAL_FEEDBACK_MAX_LENGTH = 2_000;

    @Autowired
    private InterviewProceedService interviewProceedService;
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

    /**
     * 차감은 면접 평가·점수 UPDATE를 flush한 뒤 별도 트랜잭션에서 실행되므로, 그동안 진행 트랜잭션이 회원 행 락을 쥐고 있다.
     * 유료 토큰 차감은 회원을 참조하는 token·token_purchase를 함께 갱신하는데, 이 락에 막히지 않고 끝나야 한다.
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
}
