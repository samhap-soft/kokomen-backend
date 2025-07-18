package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QuestionAndAnswersTest {

    @Test
    void curQuestionId가_현재_질문이_아니면_예외가_발생한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        List<Question> questions = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> QuestionFixtureBuilder.builder()
                        .id((long) i)
                        .build())
                .toList();

        List<Answer> answers = List.of(
                AnswerFixtureBuilder.builder()
                        .question(questions.get(0))
                        .id(1L)
                        .build()
        );

        String curAnswerContent = "현재 답변";

        // when & then
        assertThatThrownBy(() -> new QuestionAndAnswers(
                questions,
                answers,
                curAnswerContent,
                questions.get(questions.size() - 2).getId(),
                interview
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("현재 질문이 아닙니다. 현재 질문 id: " + questions.get(1).getId());
    }

    @Test
    void 인터뷰가_종료된_경우_예외가_발생한다() {
        // given
        int maxQuestionCount = Interview.MIN_ALLOWED_MAX_QUESTION_COUNT;
        Interview interview = InterviewFixtureBuilder.builder()
                .maxQuestionCount(maxQuestionCount)
                .build();

        List<Question> questions = IntStream.rangeClosed(1, maxQuestionCount)
                .mapToObj(i -> QuestionFixtureBuilder.builder()
                        .id((long) i)
                        .build())
                .toList();

        List<Answer> answers = IntStream.rangeClosed(1, maxQuestionCount)
                .mapToObj(i -> AnswerFixtureBuilder.builder()
                        .question(questions.get(i - 1))
                        .id((long) i)
                        .build())
                .toList();

        String curAnswerContent = "추가 답변";

        // when & then
        assertThatThrownBy(() -> new QuestionAndAnswers(
                questions,
                answers,
                curAnswerContent,
                questions.get(questions.size() - 1).getId(),
                interview
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("인터뷰가 종료되었습니다. 더 이상 답변 받을 수 없습니다.");
    }

    @Test
    void 질문과_답변의_갯수가_일치하지_않으면_예외가_발생한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        List<Question> questions = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> QuestionFixtureBuilder.builder()
                        .id((long) i)
                        .build())
                .toList();

        List<Answer> answers = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> AnswerFixtureBuilder.builder()
                        .question(questions.get(i - 1))
                        .id((long) i)
                        .build())
                .toList();

        String curAnswerContent = "현재 답변";

        // when & then
        assertThatThrownBy(() -> new QuestionAndAnswers(
                questions,
                answers,
                curAnswerContent,
                questions.get(questions.size() - 1).getId(),
                interview
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("질문과 답변의 개수가 일치하지 않습니다.");
    }

    @Test
    void 생성자_성공_테스트() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        List<Question> questions = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> QuestionFixtureBuilder.builder()
                        .id((long) i)
                        .build())
                .toList();

        List<Answer> answers = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> AnswerFixtureBuilder.builder()
                        .question(questions.get(i - 1))
                        .id((long) i)
                        .build())
                .toList();

        String curAnswerContent = "현재 답변";

        // when
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(
                questions,
                answers,
                curAnswerContent,
                questions.get(questions.size() - 1).getId(),
                interview
        );

        // then
        assertAll(
                () -> assertThat(questionAndAnswers.getQuestions())
                        .extracting(Question::getId)
                        .containsExactly(1L, 2L, 3L),
                () -> assertThat(questionAndAnswers.getPrevAnswers())
                        .extracting(Answer::getId)
                        .containsExactly(1L, 2L)
        );
    }

    @Test
    void 전체_점수_계산_테스트() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        List<Question> questions = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> QuestionFixtureBuilder.builder()
                        .id((long) i)
                        .build())
                .toList();

        List<Answer> answers = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> AnswerFixtureBuilder.builder()
                        .question(questions.get(i - 1))
                        .id((long) i)
                        .answerRank(AnswerRank.B)
                        .build())
                .toList();

        String curAnswerContent = "현재 답변";

        // when
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(
                questions,
                answers,
                curAnswerContent,
                questions.get(questions.size() - 1).getId(),
                interview
        );

        // then
        assertThat(questionAndAnswers.calculateTotalScore(AnswerRank.A.getScore()))
                .isEqualTo(AnswerRank.B.getScore() * 2 + AnswerRank.A.getScore());
    }

}
