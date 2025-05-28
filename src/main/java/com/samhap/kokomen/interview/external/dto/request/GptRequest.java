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

    public static GptRequest createGptRequest(List<Message> messages) {
        return new GptRequest("gpt-4o-mini", messages, createTools(), createToolChoice());
    }

    private static List<Tool> createTools() {
        return List.of(new Tool("function", new GptFunction("generate_feedback", createParams())));
    }

    private static GptFunctionParameters createParams() {
        return new GptFunctionParameters(
                "object",
                createProperties(),
                List.of("rank", "feedback", "next_question")
        );
    }

    private static Map<String, FunctionParamProperty> createProperties() {
        return Map.of(
                "rank", new FunctionParamProperty("string"),
                "feedback", new FunctionParamProperty("string"),
                "next_question", new FunctionParamProperty("string")
        );
    }

    private static ToolChoice createToolChoice() {
        return new ToolChoice("function", new ToolChoiceFunction("generate_feedback"));
    }
}
