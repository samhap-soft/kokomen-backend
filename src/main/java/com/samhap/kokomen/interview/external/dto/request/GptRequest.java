package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.interview.tool.InterviewToolSchemas;
import java.util.List;

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
        // GPT는 한 번의 호출로 feedback까지 생성하므로 feedbackInline=true
        ToolSchema schema = InterviewToolSchemas.proceed(true);
        return new GptRequest(GPT_MODEL, gptMessages,
                GptToolRenderer.renderTools(schema), GptToolRenderer.renderToolChoice(schema), temperature);
    }

    public static GptRequest createEndGptRequest(List<GptMessage> gptMessages, double temperature) {
        ToolSchema schema = InterviewToolSchemas.end();
        return new GptRequest(GPT_MODEL, gptMessages,
                GptToolRenderer.renderTools(schema), GptToolRenderer.renderToolChoice(schema), temperature);
    }
}
