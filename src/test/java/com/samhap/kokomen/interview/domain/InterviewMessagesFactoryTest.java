package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.fixture.interview.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewMessagesFactoryTest {

    @Test
    void 인터뷰_진행을_위해_GPT에게_보낼_메시지를_생성할_수_있다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        Question question1 = QuestionFixtureBuilder.builder()
                .id(1L)
                .content("첫 번째 질문")
                .build();

        Question question2 = QuestionFixtureBuilder.builder()
                .id(2L)
                .content("두 번째 질문")
                .build();

        Answer answer = AnswerFixtureBuilder.builder()
                .id(1L)
                .content("첫 번째 답변")
                .question(question1)
                .build();

        List<Question> questions = List.of(question1, question2);
        List<Answer> prevAnswers = List.of(answer);
        String curAnswerContent = "현재 답변";

        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(questions, prevAnswers, curAnswerContent, question2.getId(), interview);

        List<GptMessage> expectedGptMessages = List.of(
                new GptMessage("system", GptSystemMessageConstant.PROCEED_SYSTEM_MESSAGE),
                new GptMessage("assistant", "첫 번째 질문"),
                new GptMessage("user", "첫 번째 답변"),
                new GptMessage("assistant", "두 번째 질문"),
                new GptMessage("user", "현재 답변")
        );

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptProceedMessages(questionAndAnswers);

        // then
        assertThat(gptMessages).isEqualTo(expectedGptMessages);
    }

    @Test
    void 인터뷰_종료를_위해_GPT에게_보낼_메시지를_생성할_수_있다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .build();

        Question question1 = QuestionFixtureBuilder.builder()
                .id(1L)
                .content("첫 번째 질문")
                .build();

        Question question2 = QuestionFixtureBuilder.builder()
                .id(2L)
                .content("두 번째 질문")
                .build();

        Answer answer = AnswerFixtureBuilder.builder()
                .id(1L)
                .content("첫 번째 답변")
                .question(question1)
                .build();

        List<Question> questions = List.of(question1, question2);
        List<Answer> prevAnswers = List.of(answer);
        String curAnswerContent = "현재 답변";

        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(questions, prevAnswers, curAnswerContent, question2.getId(), interview);

        List<GptMessage> expectedGptMessages = List.of(
                new GptMessage("system", GptSystemMessageConstant.END_SYSTEM_MESSAGE),
                new GptMessage("assistant", "첫 번째 질문"),
                new GptMessage("user", "첫 번째 답변"),
                new GptMessage("assistant", "두 번째 질문"),
                new GptMessage("user", "현재 답변")
        );

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptEndMessages(questionAndAnswers);

        // then
        assertThat(gptMessages).isEqualTo(expectedGptMessages);
    }
}
