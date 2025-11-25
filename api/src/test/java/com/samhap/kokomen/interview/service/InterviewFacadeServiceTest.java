package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.proceedstate.InterviewProceedStateResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.service.TokenService;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewFacadeServiceTest extends BaseTest {

    @Autowired
    private InterviewFacadeService interviewFacadeService;
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
    private RedisService redisService;
    @Autowired
    private TokenService tokenService;

    @Test
    void 인터뷰를_진행할_때_마지막_답변이_아니면_다음_꼬리_질문과_현재_답변에_대한_피드백을_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        int freeTokenCount = tokenService.readFreeTokenCount(member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().build());
        String nextQuestion = "스레드 안전하다는 것은 무엇인가요?";
        AnswerRank curAnswerRank = AnswerRank.A;

        GptResponse gptResponse = GptResponseFixtureBuilder.builder()
                .answerRank(curAnswerRank)
                .nextQuestion(nextQuestion)
                .buildProceed();
        when(gptClient.requestToGpt(any())).thenReturn(gptResponse);
        BedrockResponse bedrockResponse = BedrockResponseFixtureBuilder.builder()
                .answerRank(curAnswerRank)
                .nextQuestion(nextQuestion)
                .buildProceed();
        when(bedrockClient.requestToBedrock(any())).thenReturn(bedrockResponse);

        InterviewProceedResponse expected = new InterviewProceedResponse(curAnswerRank, question.getId() + 1,
                nextQuestion);

        // when
        Optional<InterviewProceedResponse> actual = interviewFacadeService.proceedInterview(
                interview.getId(), question.getId(), new AnswerRequest("프로세스는 무겁고, 스레드는 가벼워요."),
                new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(actual).contains(expected),
                () -> assertThat(questionRepository.existsById(question.getId() + 1)).isTrue(),
                () -> assertThat(tokenService.readFreeTokenCount(member.getId())).isEqualTo(freeTokenCount - 1)
        );
    }

    @Test
    void 인터뷰를_진행할_때_마지막_답변이면_현재_답변에_대한_피드백과_응답한다() {
        // given
        AnswerRank answerRank = AnswerRank.B;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        int freeTokenCount = tokenService.readFreeTokenCount(member.getId());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question1).answerRank(answerRank).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).answerRank(answerRank).build());
        String totalFeedback = "스레드 안전하다는 것은 무엇인가요?";

        GptResponse gptResponse = GptResponseFixtureBuilder.builder()
                .totalFeedback(totalFeedback)
                .answerRank(answerRank)
                .buildEnd();
        when(gptClient.requestToGpt(any())).thenReturn(gptResponse);

        BedrockResponse bedrockResponse = BedrockResponseFixtureBuilder.builder()
                .totalFeedback(totalFeedback)
                .answerRank(answerRank)
                .buildEnd();
        when(bedrockClient.requestToBedrock(any())).thenReturn(bedrockResponse);

        // when
        Optional<InterviewProceedResponse> actual = interviewFacadeService.proceedInterview(
                interview.getId(), question3.getId(), new AnswerRequest("프로세스는 무겁고, 스레드는 가벼워요."),
                new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(questionRepository.existsById(question3.getId() + 1)).isFalse(),
                () -> assertThat(interviewRepository.findById(interview.getId()).get().getTotalFeedback()).isEqualTo(
                        totalFeedback),
                () -> assertThat(interviewRepository.findById(interview.getId()).get().getTotalScore()).isEqualTo(
                        answerRank.getScore() * 3),
                () -> assertThat(memberRepository.findById(member.getId()).get().getScore()).isEqualTo(
                        member.getScore() + answerRank.getScore() * 3),
                () -> assertThat(tokenService.readFreeTokenCount(member.getId())).isEqualTo(freeTokenCount - 1)
        );
    }

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
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().build());

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(),
                question2.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewFacadeService.findInterviewProceedState(interview.getId(), question1.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId())))
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

        String interviewProceedStateKey = InterviewFacadeService.createInterviewProceedStateKey(interview.getId(),
                question3.getId());
        redisService.setValue(interviewProceedStateKey, InterviewProceedState.COMPLETED.name(), Duration.ofSeconds(10));

        // when & then
        assertThatThrownBy(() -> interviewFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                InterviewMode.TEXT,
                new MemberAuth(member.getId())))
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
                interviewFacadeService.findInterviewProceedState(interview.getId(), question2.getId(),
                        InterviewMode.TEXT, new MemberAuth(member.getId()));

        // then
        assertThat(interviewProceedState.proceedState()).isEqualTo(InterviewProceedState.LLM_FAILED);
    }

    @Test
    void 아직_좋아요를_누르지_않은_인터뷰에_좋아요를_요청할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());

        // when
        interviewFacadeService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() + 1);
    }

    @Disabled
    @Test
    void 이미_좋아요를_누른_인터뷰에_좋아요를_요청하면_예외가_발생한다() {
        // given
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());
        interviewFacadeService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when & then
        assertThatThrownBy(
                () -> interviewFacadeService.likeInterview(interview.getId(), new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 좋아요를 누른 인터뷰입니다.");
    }

    @Test
    void 이미_좋아요를_누른_인터뷰에_대해_좋아요를_취소할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());
        interviewFacadeService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when
        interviewFacadeService.unlikeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount());
    }

    @Test
    void 인터뷰에_좋아요를_누르면_최신_좋아요_수로_이벤트가_발행된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());
        MemberAuth memberAuth = new MemberAuth(member.getId());
        Long beforeLikeCount = interview.getLikeCount();

        // when
        interviewFacadeService.likeInterview(interview.getId(), memberAuth);

        // then
        Interview updatedInterview = interviewRepository.findById(interview.getId()).get();
        assertThat(updatedInterview.getLikeCount()).isEqualTo(beforeLikeCount + 1);
    }
}
