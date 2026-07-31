package com.samhap.kokomen.global.fixture.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * GPT 폴백 목 전용. GPT 클라이언트가 String을 반환하고 parseGptResponse가 이중 인코딩을 벗기므로
 * arguments(단일 인코딩)와 doubleEncoded(이중 인코딩) 두 가지를 제공한다.
 * 키 집합은 Bedrock 픽스처와 동일해야 한다(같은 tool 스키마를 쓰므로 차원당 4키 + total_feedback).
 */
public class ResumeAnalysisGptResponseFixtureBuilder {

    private static final int DEFAULT_QUESTION_COUNT = 5;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private int questionCount = DEFAULT_QUESTION_COUNT;
    private String totalFeedback = "종합 총평";

    public static ResumeAnalysisGptResponseFixtureBuilder builder() {
        return new ResumeAnalysisGptResponseFixtureBuilder();
    }

    public ResumeAnalysisGptResponseFixtureBuilder questionCount(int questionCount) {
        this.questionCount = questionCount;
        return this;
    }

    public ResumeAnalysisGptResponseFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public String buildEvaluationArguments(boolean jdProvided) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (ResumeAnalysisDimension dimension : ResumeAnalysisSchema.dimensions(jdProvided)) {
            putDimension(arguments, dimension.toolKey(), defaultScoreOf(dimension));
        }
        arguments.put("total_feedback", totalFeedback);
        return writeValueAsString(arguments);
    }

    public String buildEvaluationDoubleEncoded(boolean jdProvided) {
        return writeValueAsString(buildEvaluationArguments(jdProvided));
    }

    public String buildQuestionsArguments() {
        List<Map<String, String>> questions = IntStream.rangeClosed(1, questionCount)
                .mapToObj(index -> Map.of("question", "질문 " + index, "reason", "이유 " + index))
                .toList();
        return writeValueAsString(Map.of("questions", questions));
    }

    // _reasoning을 포함해 차원당 4키다 — 빼면 JD 포함 16키가 되어 requiredFieldCount(true)=21과 어긋난다.
    private void putDimension(Map<String, Object> arguments, String key, int score) {
        arguments.put(key + "_reasoning", "사고 과정");
        arguments.put(key + "_score", score);
        arguments.put(key + "_reason", reasonItems("근거"));
        arguments.put(key + "_improvements", reasonItems("보완"));
    }

    private List<String> reasonItems(String prefix) {
        List<String> items = new ArrayList<>();
        items.add(prefix + "1");
        items.add(prefix + "2");
        return items;
    }

    private int defaultScoreOf(ResumeAnalysisDimension dimension) {
        return switch (dimension) {
            case PROBLEM_SOLVING -> 90;
            case PROJECT_EXPERIENCE -> 80;
            case TECHNICAL_SKILLS -> 70;
            case SOFT_SKILLS -> 60;
            case JD_FIT -> 50;
        };
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이력서 분석 GPT 픽스처 직렬화 실패", e);
        }
    }
}
