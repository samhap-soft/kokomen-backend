package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

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
    @Autowired
    private InterviewLikeRepository interviewLikeRepository;

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

    @Test
    void 아직_좋아요를_누르지_않은_인터뷰에_좋아요를_요청할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0).build());

        // when
        interviewService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() + 1);
    }

    @Test
    void 이미_좋아요를_누른_인터뷰에_좋아요를_요청하면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0).build());
        interviewService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when & then
        assertThatThrownBy(() -> interviewService.likeInterview(interview.getId(), new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 좋아요를 누른 인터뷰입니다.");
    }

    @Test
    void 이미_좋아요를_누른_인터뷰에_대해_좋아요를_취소할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1).build());
        interviewService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when
        interviewService.unlikeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount());
    }

    @Test
    void 좋아요를_누르지_않은_인터뷰에_대해_좋아요를_취소하면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1).build());

        // when & then
        assertThatThrownBy(() -> interviewService.unlikeInterview(interview.getId(), new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("좋아요를 누르지 않은 인터뷰입니다.");
    }

    @Test
    void 내_인터뷰_목록을_조회할_때_완료된_인터뷰의_경우에만_이미_좋아요를_눌렀는지_여부도_함께_조회된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1).build());
        Interview interview2 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.IN_PROGRESS).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview1).member(member).build());

        // when
        List<InterviewSummaryResponse> interviewSummaryResponses = interviewService.findMyInterviews(new MemberAuth(member.getId()), null,
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id")));

        // then
        assertAll(
                () -> assertThat(interviewSummaryResponses).hasSize(2),
                () -> assertThat(interviewSummaryResponses.get(0).interviewId()).isEqualTo(interview2.getId()),
                () -> assertThat(interviewSummaryResponses.get(0).alreadyLiked()).isNull(),
                () -> assertThat(interviewSummaryResponses.get(1).interviewId()).isEqualTo(interview1.getId()),
                () -> assertThat(interviewSummaryResponses.get(1).alreadyLiked()).isTrue()
        );
    }

    @Test
    void 남의_인터뷰_목록을_조회할_때_이미_좋아요를_눌렀는지_여부도_함께_조회된다() {
        // given
        Member readerMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member targetMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(targetMember).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1).build());
        Interview interview2 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(targetMember).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview1).member(readerMember).build());

        // when
        List<InterviewSummaryResponse> interviewSummaryResponses = interviewService.findMemberInterviews(
                targetMember.getId(),
                new MemberAuth(readerMember.getId()),
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id"))
        );

        // then
        assertAll(
                () -> assertThat(interviewSummaryResponses).hasSize(2),
                () -> assertThat(interviewSummaryResponses.get(0).interviewId()).isEqualTo(interview2.getId()),
                () -> assertThat(interviewSummaryResponses.get(0).alreadyLiked()).isFalse(),
                () -> assertThat(interviewSummaryResponses.get(1).interviewId()).isEqualTo(interview1.getId()),
                () -> assertThat(interviewSummaryResponses.get(1).alreadyLiked()).isTrue()
        );
    }
}
