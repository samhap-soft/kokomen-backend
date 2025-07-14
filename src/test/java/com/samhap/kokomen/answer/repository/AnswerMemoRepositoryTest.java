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
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class AnswerMemoRepositoryTest extends BaseTest {

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
    @Autowired
    private AnswerMemoRepository answerMemoRepository;

    @ParameterizedTest
    @MethodSource("answerMemoExistsProvider")
    void 답변_메모_존재_여부_테스트(boolean expected, AnswerMemoState answerMemoState) {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());
        answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer).answerMemoVisibility(AnswerMemoVisibility.PUBLIC).build());

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
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Question question = questionRepository.save(QuestionFixtureBuilder.builder().interview(interview).build());
        Answer answer = answerRepository.save(AnswerFixtureBuilder.builder().question(question).build());
        AnswerMemo answerMemo = answerMemoRepository.save(AnswerMemoFixtureBuilder.builder().answer(answer).answerMemoState(AnswerMemoState.SUBMITTED).build());

        // when
        Optional<AnswerMemo> result = answerMemoRepository.findByAnswerIdAndAnswerMemoState(answer.getId(), answerMemoState);

        // then
        assertThat(result.isPresent()).isEqualTo(expected);
    }

    static Stream<Arguments> answerMemoFindProvider() {
        return Stream.of(
                Arguments.of(AnswerMemoState.SUBMITTED, true),
                Arguments.of(AnswerMemoState.TEMP, false)
        );
    }
}
