package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;

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

    public BedrockConverseResponse buildProceed() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("rank", Document.fromString(answerRank != null ? answerRank.name() : "A"));
        input.put("next_question",
                Document.fromString(nextQuestion != null ? nextQuestion : "스레드 안전하다는 것은 무엇인가요?"));
        return new BedrockConverseResponse(Document.fromMap(input));
    }

    public BedrockConverseResponse buildEnd() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("rank", Document.fromString(answerRank != null ? answerRank.name() : "A"));
        input.put("feedback", Document.fromString(feedback != null ? feedback : "좋은 답변입니다."));
        input.put("total_feedback",
                Document.fromString(totalFeedback != null ? totalFeedback : "전체적으로 완벽한 대답입니다."));
        return new BedrockConverseResponse(Document.fromMap(input));
    }
}
