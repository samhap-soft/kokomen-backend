package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.interview.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;

public class BedrockResponseFixtureBuilder {

    private AnswerRank answerRank;
    private String feedback;
    private String nextQuestion;
    private String totalFeedback;

    public static BedrockResponseFixtureBuilder builder() {
        return new BedrockResponseFixtureBuilder();
    }

    public BedrockResponseFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public BedrockResponseFixtureBuilder feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public BedrockResponseFixtureBuilder nextQuestion(String nextQuestion) {
        this.nextQuestion = nextQuestion;
        return this;
    }

    public BedrockResponseFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public BedrockResponse buildProceed() {
        String content = """
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
        return new BedrockResponse(content);
    }

    public BedrockResponse buildEnd() {
        String content = """
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
        return new BedrockResponse(content);
    }
} 