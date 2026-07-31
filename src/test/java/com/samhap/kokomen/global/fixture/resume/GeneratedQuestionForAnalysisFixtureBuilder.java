package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import java.util.List;
import java.util.stream.IntStream;

public class GeneratedQuestionForAnalysisFixtureBuilder {

    private static final int DEFAULT_QUESTION_COUNT = 5;

    private ResumeAnalysis analysis;
    private String content;
    private String reason;
    private Integer questionOrder;

    public static GeneratedQuestionForAnalysisFixtureBuilder builder() {
        return new GeneratedQuestionForAnalysisFixtureBuilder();
    }

    public static List<GeneratedQuestion> five(ResumeAnalysis analysis) {
        return IntStream.range(0, DEFAULT_QUESTION_COUNT)
                .mapToObj(questionOrder -> builder()
                        .analysis(analysis)
                        .questionOrder(questionOrder)
                        .build())
                .toList();
    }

    public GeneratedQuestionForAnalysisFixtureBuilder analysis(ResumeAnalysis analysis) {
        this.analysis = analysis;
        return this;
    }

    public GeneratedQuestionForAnalysisFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public GeneratedQuestionForAnalysisFixtureBuilder reason(String reason) {
        this.reason = reason;
        return this;
    }

    public GeneratedQuestionForAnalysisFixtureBuilder questionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
        return this;
    }

    public GeneratedQuestion build() {
        int order = questionOrder != null ? questionOrder : 0;
        return GeneratedQuestion.forAnalysis(
                analysis,
                content != null ? content : "이력서 기반 질문 " + order,
                reason != null ? reason : "질문 선정 이유 " + order,
                order
        );
    }
}
