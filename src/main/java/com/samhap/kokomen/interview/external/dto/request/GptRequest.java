package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record GptRequest(
        String model,
        @JsonProperty("messages")
        List<GptMessage> gptMessages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        ToolChoice toolChoice,
        Double temperature
) {

    private static final String GPT_MODEL = "gpt-4.1-mini";

    public static GptRequest createProceedGptRequest(List<GptMessage> gptMessages, double temperature) {
        return new GptRequest(GPT_MODEL, gptMessages, createProceedTools(), createProceedToolChoice(), temperature);
    }

    public static GptRequest createEndGptRequest(List<GptMessage> gptMessages, double temperature) {
        return new GptRequest(GPT_MODEL, gptMessages, createEndTools(), createEndToolChoice(), temperature);
    }

    private static List<Tool> createProceedTools() {
        return List.of(new Tool("function", new GptFunction("generate_feedback", createProceedParams())));
    }

    private static List<Tool> createEndTools() {
        return List.of(new Tool("function", new GptFunction("generate_total_feedback", createEndParams())));
    }

    private static GptFunctionParameters createProceedParams() {
        return new GptFunctionParameters(
                "object",
                createProceedProperties(),
                List.of("reasoning", "rank", "feedback", "next_question")
        );
    }

    private static GptFunctionParameters createEndParams() {
        return new GptFunctionParameters(
                "object",
                createEndProperties(),
                List.of("reasoning", "rank", "feedback", "overall_summary")
        );
    }

    private static Map<String, Object> createProceedProperties() {
        return Map.of(
                "reasoning", new FunctionParamProperty("string"),
                "rank", new FunctionParamProperty("string"),
                "feedback", new FunctionParamProperty("string"),
                "next_question", new FunctionParamProperty("string")
        );
    }

    private static Map<String, Object> createEndProperties() {
        return Map.of(
                "reasoning", new FunctionParamProperty("string"),
                "rank", new FunctionParamProperty("string"),
                "feedback", new FunctionParamProperty("string"),
                "overall_summary", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "strengths", Map.of(
                                        "type", "string",
                                        "description", "면접자의 강점 1-2문장. 존댓말, 점수/랭크 미언급"),
                                "improvements", Map.of(
                                        "type", "string",
                                        "description", "보완·개선 영역 1-2문장. 존댓말, 점수/랭크 미언급"),
                                "learning_direction", Map.of(
                                        "type", "string",
                                        "description", "향후 학습 방향 1-2문장. 존댓말, 점수/랭크 미언급")
                        ),
                        "required", List.of("strengths", "improvements", "learning_direction")
                )
        );
    }

    private static ToolChoice createProceedToolChoice() {
        return new ToolChoice("function", new ToolChoiceFunction("generate_feedback"));
    }

    private static ToolChoice createEndToolChoice() {
        return new ToolChoice("function", new ToolChoiceFunction("generate_total_feedback"));
    }
}
