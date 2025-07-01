package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.response.Choice;
import com.samhap.kokomen.interview.external.dto.response.GptFunctionCall;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.Message;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import java.util.List;

public class GptResponseFixtureBuilder {

    private AnswerRank answerRank;
    private String feedback;
    private String nextQuestion;
    private String totalFeedback;

    public static GptResponseFixtureBuilder builder() {
        return new GptResponseFixtureBuilder();
    }

    public GptResponseFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public GptResponseFixtureBuilder feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public GptResponseFixtureBuilder nextQuestion(String nextQuestion) {
        this.nextQuestion = nextQuestion;
        return this;
    }

    public GptResponseFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public GptResponse buildProceed() {
        String arguments = """
                {
                  "rank": "%s",
                  "feedback": "%s",
                  "next_question": "%s"
                }
                """.formatted(
                answerRank != null ? answerRank.name() : "A",
                feedback != null ? feedback : "좋은 답변입니다.",
                nextQuestion != null ? nextQuestion : "스레드 안전하다는 것은 무엇인가요?"
        );
        return create(arguments, "generate_feedback");
    }

    public GptResponse buildEnd() {
        String arguments = """
                {
                  "rank": "%s",
                  "feedback": "%s",
                  "total_feedback": "%s"
                }
                """.formatted(
                answerRank != null ? answerRank.name() : "A",
                feedback != null ? feedback : "좋은 답변입니다.",
                totalFeedback != null ? totalFeedback : "전체적으로 완벽한 대답입니다."
        );
        return create(arguments, "generate_total_feedback");
    }

    private GptResponse create(String arguments, String gptFunctionCallName) {
        return new GptResponse(
                List.of(new Choice(
                        new Message(
                                List.of(new ToolCall(new GptFunctionCall(
                                        gptFunctionCallName,
                                        arguments
                                )))
                        )
                )));
    }
}
