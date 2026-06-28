package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.global.fixture.answer.AnswerFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.Question;
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

    @Test
    void 라이브_코테_진행을_위해_GPT에게_보낼_메시지는_코딩용_시스템_프롬프트를_사용한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.LIVE_CODING)
                .build();
        Question question = QuestionFixtureBuilder.builder().id(1L).content("코딩 문제").build();
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(List.of(question), List.of(), "제출한 코드",
                question.getId(), interview);

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptProceedMessages(questionAndAnswers);

        // then
        assertThat(gptMessages.get(0))
                .isEqualTo(new GptMessage("system", GptSystemMessageConstant.CODING_PROCEED_SYSTEM_MESSAGE));
    }

    @Test
    void 라이브_코테_종료를_위해_GPT에게_보낼_메시지는_코딩용_시스템_프롬프트를_사용한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.LIVE_CODING)
                .build();
        Question question = QuestionFixtureBuilder.builder().id(1L).content("코딩 문제").build();
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(List.of(question), List.of(), "제출한 코드",
                question.getId(), interview);

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptEndMessages(questionAndAnswers);

        // then
        assertThat(gptMessages.get(0))
                .isEqualTo(new GptMessage("system", GptSystemMessageConstant.CODING_END_SYSTEM_MESSAGE));
    }

    @Test
    void 인성_면접_진행을_위해_GPT에게_보낼_메시지는_인성용_시스템_프롬프트를_사용한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.PERSONALITY)
                .build();
        Question question = QuestionFixtureBuilder.builder().id(1L).content("인성 질문").build();
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(List.of(question), List.of(), "제 경험은",
                question.getId(), interview);

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptProceedMessages(questionAndAnswers);

        // then
        assertThat(gptMessages.get(0))
                .isEqualTo(new GptMessage("system", GptSystemMessageConstant.PERSONALITY_PROCEED_SYSTEM_MESSAGE));
    }

    @Test
    void 인성_면접_종료를_위해_GPT에게_보낼_메시지는_인성용_시스템_프롬프트를_사용한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.PERSONALITY)
                .build();
        Question question = QuestionFixtureBuilder.builder().id(1L).content("인성 질문").build();
        QuestionAndAnswers questionAndAnswers = new QuestionAndAnswers(List.of(question), List.of(), "제 경험은",
                question.getId(), interview);

        // when
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptEndMessages(questionAndAnswers);

        // then
        assertThat(gptMessages.get(0))
                .isEqualTo(new GptMessage("system", GptSystemMessageConstant.PERSONALITY_END_SYSTEM_MESSAGE));
    }
}
