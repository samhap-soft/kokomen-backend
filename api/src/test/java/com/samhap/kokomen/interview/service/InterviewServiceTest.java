package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerMemoRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.answer.AnswerLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.answer.AnswerMemoFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    @Autowired
    private AnswerLikeRepository answerLikeRepository;
    @Autowired
    private AnswerMemoRepository answerMemoRepository;

    @Test
    void 좋아요를_누르지_않은_인터뷰에_대해_좋아요를_취소하면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());

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
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1L).build());
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
                () -> assertThat(interviewSummaryResponses.get(0).interviewAlreadyLiked()).isNull(),
                () -> assertThat(interviewSummaryResponses.get(1).interviewId()).isEqualTo(interview1.getId()),
                () -> assertThat(interviewSummaryResponses.get(1).interviewAlreadyLiked()).isTrue()
        );
    }

    @Test
    void 남의_인터뷰_목록을_조회할_때_이미_좋아요를_눌렀는지_여부도_함께_조회된다() {
        // given
        Member readerMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member targetMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(targetMember).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1L)
                        .build());
        Interview interview2 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(targetMember).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview1).member(readerMember).build());

        // when
        List<InterviewSummaryResponse> interviewSummaryResponses = interviewService.findOtherMemberInterviews(
                targetMember.getId(),
                new MemberAuth(readerMember.getId()),
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id"))
        ).interviewSummaries();

        // then
        assertAll(
                () -> assertThat(interviewSummaryResponses).hasSize(2),
                () -> assertThat(interviewSummaryResponses.get(0).interviewId()).isEqualTo(interview2.getId()),
                () -> assertThat(interviewSummaryResponses.get(0).interviewAlreadyLiked()).isFalse(),
                () -> assertThat(interviewSummaryResponses.get(1).interviewId()).isEqualTo(interview1.getId()),
                () -> assertThat(interviewSummaryResponses.get(1).interviewAlreadyLiked()).isTrue()
        );
    }

    @Test
    void 남의_인터뷰_결과를_조회할_때_답변과_인터뷰_각각에_대해_이미_좋아요를_눌렀는지_여부도_함께_조회된다() {
        // given
        Member readerMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).build());
        Member targetMember = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(targetMember).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview).member(readerMember).build());
        answerLikeRepository.save(AnswerLikeFixtureBuilder.builder().member(readerMember).answer(answer1).build());
        answerLikeRepository.save(AnswerLikeFixtureBuilder.builder().member(readerMember).answer(answer2).build());

        // when
        InterviewResultResponse results = interviewService.findOtherMemberInterviewResult(interview.getId(), new MemberAuth(readerMember.getId()),
                new ClientIp("1.1.1.1"));

        // then
        assertAll(
                () -> assertThat(results.interviewAlreadyLiked()).isTrue(),
                () -> assertThat(results.feedbacks().get(0).answerAlreadyLiked()).isTrue(),
                () -> assertThat(results.feedbacks().get(1).answerAlreadyLiked()).isTrue(),
                () -> assertThat(results.feedbacks().get(2).answerAlreadyLiked()).isFalse()
        );
    }

    @MethodSource("providePageSizeAndInterviewCountAndTotalPageCount")
    @ParameterizedTest
    void 다른_사람의_인터뷰_목록을_조회할_때_전체_페이지_수를_계산해서_응답한다(int pageSize, long interviewCount, long totalPageCount) {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        for (int i = 0; i < interviewCount; i++) {
            interviewRepository.save(
                    InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        }

        // when
        long actualTotalPageCount = interviewService.findOtherMemberInterviews(
                member.getId(), MemberAuth.notAuthenticated(), PageRequest.of(0, pageSize, Sort.by(Direction.DESC, "id"))
        ).totalPageCount();

        // then
        assertThat(actualTotalPageCount).isEqualTo(totalPageCount);
    }

    private static Stream<Arguments> providePageSizeAndInterviewCountAndTotalPageCount() {
        return Stream.of(
                Arguments.of(10, 0, 0),
                Arguments.of(10, 1, 1),
                Arguments.of(10, 11, 2),
                Arguments.of(10, 20, 2),
                Arguments.of(10, 21, 3)
        );
    }

    @Test
    void 내_인터뷰_목록_조회시_완료된_인터뷰는_답변_메모_공개_여부와_상관_없이_작성된_답변_메모_개수를_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findMyInterviews(new MemberAuth(member.getId()), null,
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id")));

        // then
        assertThat(responses.get(0).submittedAnswerMemoCount()).isEqualTo(2);
    }

    @Test
    void 내_인터뷰_목록_조회시_작성된_답변_메모_개수를_응답할_때_작성_중인_답변_메모는_세지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.TEMP)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.TEMP)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findMyInterviews(new MemberAuth(member.getId()), null,
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id")));

        // then
        assertThat(responses.get(0).submittedAnswerMemoCount()).isEqualTo(0);
    }

    @MethodSource("provideAnswerMemoStateAndHasTempAnswerMemo")
    @ParameterizedTest
    void 내_인터뷰_목록_조회시_완료된_인터뷰는_작성중인_답변_메모가_존재하는지_응답한다(AnswerMemoState answerMemoState, boolean hasTempAnswerMemo) {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());

        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(answerMemoState)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findMyInterviews(new MemberAuth(member.getId()), null,
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id")));

        // then
        assertThat(responses.get(0).hasTempAnswerMemo()).isEqualTo(hasTempAnswerMemo);
    }

    private static Stream<Arguments> provideAnswerMemoStateAndHasTempAnswerMemo() {
        return Stream.of(
                Arguments.of(AnswerMemoState.TEMP, true),
                Arguments.of(AnswerMemoState.SUBMITTED, false)
        );
    }

    @Test
    void 내_인터뷰_목록_조회시_완료되지_않은_인터뷰는_작성된_답변_메모_개수와_작성중인_답변_메모가_존재하는지_여부를_응답하지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.IN_PROGRESS).likeCount(1L).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findMyInterviews(new MemberAuth(member.getId()), null,
                PageRequest.of(0, 10, Sort.by(Direction.DESC, "id")));

        // then
        assertAll(
                () -> assertThat(responses.get(0).submittedAnswerMemoCount()).isNull(),
                () -> assertThat(responses.get(0).hasTempAnswerMemo()).isNull()
        );
    }

    @Test
    void 다른_사람_인터뷰_목록_조회시_작성이_완료된_공개_답변_메모_개수만_응답한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).maxQuestionCount(4).interviewState(InterviewState.FINISHED)
                        .likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());
        Question question4 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer4 = answerRepository.save(AnswerFixtureBuilder.builder().question(question4).build());

        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer3).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer4).answerMemoState(AnswerMemoState.TEMP)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview1).member(member).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findOtherMemberInterviews(
                member.getId(), MemberAuth.notAuthenticated(), PageRequest.of(0, 10, Sort.by(Direction.DESC, "id"))
        ).interviewSummaries();

        // then
        assertAll(
                () -> assertThat(responses.get(0).submittedAnswerMemoCount()).isEqualTo(2),
                () -> assertThat(responses.get(0).hasTempAnswerMemo()).isNull()
        );
    }

    @Test
    void 다른_사람_인터뷰_목록_조회시_작성중인_답변_존재_여부는_응답하지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).maxQuestionCount(4).interviewState(InterviewState.FINISHED)
                        .likeCount(1L).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());
        Question question4 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview1).build());
        Answer answer4 = answerRepository.save(AnswerFixtureBuilder.builder().question(question4).build());

        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.TEMP)
                .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer3).answerMemoState(AnswerMemoState.SUBMITTED)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer4).answerMemoState(AnswerMemoState.TEMP)
                .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().interview(interview1).member(member).build());

        // when
        List<InterviewSummaryResponse> responses = interviewService.findOtherMemberInterviews(
                member.getId(), MemberAuth.notAuthenticated(), PageRequest.of(0, 10, Sort.by(Direction.DESC, "id"))
        ).interviewSummaries();
        // then
        assertThat(responses.get(0).hasTempAnswerMemo()).isNull();
    }
}
