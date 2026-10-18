package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequestV2;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewProceedFacadeServiceTest extends BaseTest {

    @Autowired
    private InterviewProceedFacadeService interviewProceedFacadeService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private RedisService redisService;

    @Test
    void 진행중인_인터뷰_진행_상황을_폴링으로_조회할_때_현재_질문_id가_아니라면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.A).build());
        questionRepository.save(QuestionFixtureBuilder.builder().build());

        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interview.getId(),
                question2.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question1.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: 2");
    }

    @Test
    void 완료된_인터뷰_진행_상황을_폴링으로_조회할_때_현재_질문_id가_아니라면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(AnswerRank.A).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).answerRank(AnswerRank.A).build());

        String interviewProceedStateKey = InterviewProceedFacadeService.createInterviewProceedStateKey(interview.getId(),
                question3.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: 3");
    }

    @Test
    void 인터뷰_진행_상황을_폴링으로_조회할_때_레디스에_LLM_진행_상태가_없고_답변이_없는_질문으로_요청이_오면_FAILED를_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(AnswerRank.B).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());

        // when
        InterviewProceedStateResponse interviewProceedState =
                interviewProceedFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                        InterviewMode.TEXT, new MemberAuth(member.getId()), new ClientIp("0.0.0.0"));

        // then
        assertThat(interviewProceedState.proceedState()).isEqualTo(InterviewProceedState.LLM_FAILED);
    }

    @Test
    void 현재_질문이_아닌_질문에_답변을_제출하면_예외가_발생하고_진행_락이_해제된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        AnswerRequestV2 answerRequest = new AnswerRequestV2("이미 답변한 질문에 다시 보낸 답변입니다.", InterviewMode.TEXT);

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.proceedInterviewByBedrockFlow(interview.getId(),
                question1.getId(), answerRequest, new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: " + question2.getId());
        String lockKey = InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
        assertThat(redisService.get(lockKey, String.class)).isEmpty();
    }

    @Test
    void 종료된_인터뷰에_답변을_제출하면_예외가_발생하고_진행_락이_해제된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).maxQuestionCount(3)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());
        AnswerRequestV2 answerRequest = new AnswerRequestV2("마지막 질문에 다시 보낸 답변입니다.", InterviewMode.TEXT);

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.proceedInterviewByBedrockFlow(interview.getId(),
                question3.getId(), answerRequest, new MemberAuth(member.getId()), new ClientIp("0.0.0.0")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("인터뷰가 종료되었습니다. 더 이상 답변 받을 수 없습니다.");
        String lockKey = InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
        assertThat(redisService.get(lockKey, String.class)).isEmpty();
    }

    @Test
    void 비회원이_현재_질문이_아닌_질문에_답변을_제출하면_예외가_발생하고_IP_진행_락이_해제된다() {
        // given
        ClientIp clientIp = new ClientIp("11.22.33.44");
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(null).rootQuestion(rootQuestion).guestIp(clientIp.address())
                        .build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        AnswerRequestV2 answerRequest = new AnswerRequestV2("이미 답변한 질문에 다시 보낸 답변입니다.", InterviewMode.TEXT);

        // when & then
        assertThatThrownBy(() -> interviewProceedFacadeService.proceedInterviewByBedrockFlow(interview.getId(),
                question1.getId(), answerRequest, MemberAuth.notAuthenticated(), clientIp))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("현재 질문이 아닙니다. 현재 질문 id: " + question2.getId());
        String lockKey = InterviewProceedFacadeService.createGuestInterviewProceedLockKey(clientIp);
        assertThat(redisService.get(lockKey, String.class)).isEmpty();
    }

    @Test
    void 현재_질문이_아닌_질문에_답변을_제출해_실패한_직후에도_현재_질문에_답변을_제출할_수_있다() {
        // given - LLM 응답은 이 테스트의 관심사가 아니므로 호출을 실패시켜 비동기 작업이 저장 없이 끝나게 한다
        when(interviewProceedBedrockClient.requestToBedrock(any()))
                .thenThrow(new ExternalApiException("Bedrock 호출 실패"));
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        MemberAuth memberAuth = new MemberAuth(member.getId());
        ClientIp clientIp = new ClientIp("0.0.0.0");
        AnswerRequestV2 answerRequest = new AnswerRequestV2("절차지향 프로그래밍과 반대되는 개념입니다.", InterviewMode.TEXT);
        assertThatThrownBy(() -> interviewProceedFacadeService.proceedInterviewByBedrockFlow(interview.getId(),
                question1.getId(), answerRequest, memberAuth, clientIp))
                .isInstanceOf(BadRequestException.class);

        // when & then
        assertThatCode(() -> interviewProceedFacadeService.proceedInterviewByBedrockFlow(interview.getId(),
                question2.getId(), answerRequest, memberAuth, clientIp))
                .doesNotThrowAnyException();

        // 비동기 작업이 끝나며 락을 풀 때까지 기다려 다음 테스트와 겹치지 않게 한다.
        String lockKey = InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
        await().atMost(Duration.ofSeconds(5)).until(() -> redisService.get(lockKey, String.class).isEmpty());
    }
}
