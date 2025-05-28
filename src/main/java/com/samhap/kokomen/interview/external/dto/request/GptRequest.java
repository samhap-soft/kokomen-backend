package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record GptRequest(
        String model,
        List<Message> messages,
        List<Tool> tools,
        @JsonProperty("tool_choice")
        ToolChoice toolChoice
) {

    public static GptRequest createProceedGptRequest(List<Message> messages) {
        return new GptRequest("gpt-4o-mini", messages, createProceedTools(), createProceedToolChoice());
    }

    public static GptRequest createEndGptRequest(List<Message> messages) {
        return new GptRequest("gpt-4o-mini", messages, createEndTools(), createEndToolChoice());
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
                List.of("rank", "feedback", "next_question")
        );
    }

    private static GptFunctionParameters createEndParams() {
        return new GptFunctionParameters(
                "object",
                createEndProperties(),
                List.of("rank", "feedback", "total_feedback")
        );
    }

    private static Map<String, FunctionParamProperty> createProceedProperties() {
        return Map.of(
                "rank", new FunctionParamProperty("string"),
                "feedback", new FunctionParamProperty("string"),
                "next_question", new FunctionParamProperty("string")
        );
    }

    private static Map<String, FunctionParamProperty> createEndProperties() {
        return Map.of(
                "rank", new FunctionParamProperty("string"),
                "feedback", new FunctionParamProperty("string"),
                "total_feedback", new FunctionParamProperty("string")
        );
    }

    private static ToolChoice createProceedToolChoice() {
        return new ToolChoice("function", new ToolChoiceFunction("generate_feedback"));
    }

    private static ToolChoice createEndToolChoice() {
        return new ToolChoice("function", new ToolChoiceFunction("generate_total_feedback"));
    }
}
