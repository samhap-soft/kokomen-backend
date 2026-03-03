package com.samhap.kokomen.answer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.answer.AnswerMemoFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class AnswerMemoRepositoryTest extends BaseTest {

    @Autowired
    AnswerMemoRepository answerMemoRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @ParameterizedTest
    @MethodSource("answerMemoExistsProvider")
    void 답변_메모_존재_여부_테스트(boolean expected, AnswerMemoState answerMemoState) {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer).answerMemoVisibility(AnswerMemoVisibility.PUBLIC)
                        .build());

        // when
        boolean exists = answerMemoRepository.existsByAnswerIdAndAnswerMemoState(answer.getId(), answerMemoState);

        // then
        assertThat(exists).isEqualTo(expected);
    }

    static Stream<Arguments> answerMemoExistsProvider() {
        return Stream.of(
                Arguments.of(true, AnswerMemoState.SUBMITTED),
                Arguments.of(false, AnswerMemoState.TEMP)
        );
    }

    // findByAnswerIdAndAnswerMemoState 테스트 작성
    @ParameterizedTest
    @MethodSource("answerMemoFindProvider")
    void 답변_메모_상태에_따른_조회_테스트(AnswerMemoState answerMemoState, boolean expected) {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer).answerMemoState(AnswerMemoState.SUBMITTED).build());

        // when
        Optional<AnswerMemo> result = answerMemoRepository.findByAnswerIdAndAnswerMemoState(answer.getId(),
                answerMemoState);

        // then
        assertThat(result.isPresent()).isEqualTo(expected);
    }

    static Stream<Arguments> answerMemoFindProvider() {
        return Stream.of(
                Arguments.of(AnswerMemoState.SUBMITTED, true),
                Arguments.of(AnswerMemoState.TEMP, false)
        );
    }

    @Test
    void 인터뷰와_답변_메모_상태로_개수를_센다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());

        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.SUBMITTED).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.TEMP).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer3).answerMemoState(AnswerMemoState.TEMP).build());

        // when
        Long submittedMemoCount = answerMemoRepository.countByAnswerQuestionInterviewAndAnswerMemoState(interview,
                AnswerMemoState.SUBMITTED);

        // then
        assertThat(submittedMemoCount).isEqualTo(2L);
    }

    @Test
    void 인터뷰와_답변_메모_상태와_답변_메모_공개_여부로_개수를_센다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());
        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());
        Question question4 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer4 = answerRepository.save(AnswerFixtureBuilder.builder().question(question4).build());
        Question question5 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer5 = answerRepository.save(AnswerFixtureBuilder.builder().question(question5).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED)
                        .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.SUBMITTED)
                        .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer3).answerMemoState(AnswerMemoState.SUBMITTED)
                        .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer4).answerMemoState(AnswerMemoState.TEMP)
                        .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer5).answerMemoState(AnswerMemoState.TEMP)
                        .answerMemoVisibility(AnswerMemoVisibility.PRIVATE).build());

        // when
        Long submittedMemoCount = answerMemoRepository.countByAnswerQuestionInterviewAndAnswerMemoStateAndAnswerMemoVisibility(
                interview,
                AnswerMemoState.SUBMITTED, AnswerMemoVisibility.PUBLIC);

        // then
        assertThat(submittedMemoCount).isEqualTo(3L);
    }

    @Test
    void 인터뷰와_답변_메모_상태로_존재하는지_확인한다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());

        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer2 = answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        Question question3 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer3 = answerRepository.save(AnswerFixtureBuilder.builder().question(question3).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.SUBMITTED).build());
        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer2).answerMemoState(AnswerMemoState.TEMP).build());

        answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer3).answerMemoState(AnswerMemoState.TEMP).build());

        // when
        Boolean tempMemoExists = answerMemoRepository.existsByAnswerQuestionInterviewAndAnswerMemoState(interview,
                AnswerMemoState.TEMP);

        // then
        assertThat(tempMemoExists).isTrue();
    }

    @Test
    void 답변과_답변_메모_상태로_답변_메모를_찾는다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());
        Question question2 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        answerRepository.save(AnswerFixtureBuilder.builder().question(question2).build());

        AnswerMemo submittedAnswerMemo = answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer1).answerMemoState(AnswerMemoState.SUBMITTED).build());

        // when
        Optional<AnswerMemo> foundSubmittedAnswerMemo = answerMemoRepository.findByAnswerAndAnswerMemoState(answer1,
                AnswerMemoState.SUBMITTED);

        // then
        assertThat(foundSubmittedAnswerMemo.get().getId()).isEqualTo(submittedAnswerMemo.getId());
    }

    @Test
    void 답변과_답변_메모_상태와_답변_메모_공개_여부로_답변_메모를_찾는다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion)
                        .interviewState(InterviewState.FINISHED).build());
        Question question1 = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer1 = answerRepository.save(AnswerFixtureBuilder.builder().question(question1).build());

        AnswerMemo submittedPublicAnswerMemo = answerMemoRepository.save(
                AnswerMemoFixtureBuilder.builder().answer(answer1)
                        .answerMemoVisibility(AnswerMemoVisibility.PUBLIC).answerMemoState(AnswerMemoState.SUBMITTED)
                        .build());

        // when
        Optional<AnswerMemo> foundSubmittedPublicAnswerMemo = answerMemoRepository.findByAnswerAndAnswerMemoStateAndAnswerMemoVisibility(
                answer1, AnswerMemoState.SUBMITTED, AnswerMemoVisibility.PUBLIC);

        // then
        assertThat(foundSubmittedPublicAnswerMemo.get().getId()).isEqualTo(submittedPublicAnswerMemo.getId());
    }
}
