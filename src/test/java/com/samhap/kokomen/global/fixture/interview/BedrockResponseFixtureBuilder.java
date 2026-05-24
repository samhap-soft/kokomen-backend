package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;

public class BedrockResponseFixtureBuilder {

    private AnswerRank answerRank;
    private String reasoning;
    private String feedback;
    private String nextQuestion;
    private String strengths;
    private String improvements;
    private String learningDirection;

    public static BedrockResponseFixtureBuilder builder() {
        return new BedrockResponseFixtureBuilder();
    }

    public BedrockResponseFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public BedrockResponseFixtureBuilder reasoning(String reasoning) {
        this.reasoning = reasoning;
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

    public BedrockResponseFixtureBuilder strengths(String strengths) {
        this.strengths = strengths;
        return this;
    }

    public BedrockResponseFixtureBuilder improvements(String improvements) {
        this.improvements = improvements;
        return this;
    }

    public BedrockResponseFixtureBuilder learningDirection(String learningDirection) {
        this.learningDirection = learningDirection;
        return this;
    }

    public BedrockConverseResponse buildProceed() {
        Map<String, Document> input = new LinkedHashMap<>();
        input.put("reasoning",
                Document.fromString(reasoning != null ? reasoning : "답변 평가 근거와 다음 질문 설계 근거입니다."));
        input.put("rank", Document.fromString(answerRank != null ? answerRank.name() : "A"));
        input.put("next_question",
                Document.fromString(nextQuestion != null ? nextQuestion : "스레드 안전하다는 것은 무엇인가요?"));
        return new BedrockConverseResponse(Document.fromMap(input));
    }

    public BedrockConverseResponse buildEnd() {
        Map<String, Document> overallSummary = new LinkedHashMap<>();
        overallSummary.put("strengths",
                Document.fromString(strengths != null ? strengths : "전체적으로 답변이 명확합니다."));
        overallSummary.put("improvements",
                Document.fromString(improvements != null ? improvements : "구체 사례를 더 보강하면 좋겠습니다."));
        overallSummary.put("learning_direction",
                Document.fromString(learningDirection != null ? learningDirection : "기초 개념 심화 학습을 권장합니다."));

        Map<String, Document> input = new LinkedHashMap<>();
        input.put("reasoning",
                Document.fromString(reasoning != null ? reasoning : "마지막 답변 평가 근거와 전체 면접 종합 평가 근거입니다."));
        input.put("rank", Document.fromString(answerRank != null ? answerRank.name() : "A"));
        input.put("feedback", Document.fromString(feedback != null ? feedback : "좋은 답변입니다."));
        input.put("overall_summary", Document.fromMap(overallSummary));
        return new BedrockConverseResponse(Document.fromMap(input));
    }
}
