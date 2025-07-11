package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewServiceTest extends BaseTest {

    @Autowired
    private InterviewService interviewService;
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

    @Test
    void 인터뷰를_진행할_때_마지막_답변이_아니면_다음_꼬리_질문과_현재_답변에_대한_피드백을_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
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

        InterviewProceedResponse expected = new InterviewProceedResponse(curAnswerRank, question.getId() + 1, nextQuestion);

        // when
        Optional<InterviewProceedResponse> actual = interviewService.proceedInterview(
                interview.getId(), question.getId(), new AnswerRequest("프로세스는 무겁고, 스레드는 가벼워요."), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(actual).contains(expected),
                () -> assertThat(questionRepository.existsById(question.getId() + 1)).isTrue(),
                () -> assertThat(memberRepository.findById(member.getId()).get().getFreeTokenCount()).isEqualTo(member.getFreeTokenCount() - 1)
        );
    }

    @Test
    void 인터뷰를_진행할_때_마지막_답변이면_현재_답변에_대한_피드백과__응답한다() {
        // given
        AnswerRank answerRank = AnswerRank.B;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
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
        Optional<InterviewProceedResponse> actual = interviewService.proceedInterview(
                interview.getId(), question3.getId(), new AnswerRequest("프로세스는 무겁고, 스레드는 가벼워요."), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(actual).isEmpty(),
                () -> assertThat(questionRepository.existsById(question3.getId() + 1)).isFalse(),
                () -> assertThat(interviewRepository.findById(interview.getId()).get().getTotalFeedback()).isEqualTo(totalFeedback),
                () -> assertThat(interviewRepository.findById(interview.getId()).get().getTotalScore()).isEqualTo(answerRank.getScore() * 3),
                () -> assertThat(memberRepository.findById(member.getId()).get().getScore()).isEqualTo(member.getScore() + answerRank.getScore() * 3),
                () -> assertThat(memberRepository.findById(member.getId()).get().getFreeTokenCount()).isEqualTo(member.getFreeTokenCount() - 1)
        );
    }
}
