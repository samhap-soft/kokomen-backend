package com.samhap.kokomen.global.external.bedrock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@Component
public class BedrockConverseClient {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockConverseProperties properties;
    private final ObjectMapper objectMapper;

    public BedrockConverseClient(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockConverseProperties properties,
            ObjectMapper objectMapper
    ) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ConverseResponse converse(
            List<SystemContentBlock> systemMessages,
            List<Message> messages,
            ToolConfiguration toolConfiguration,
            int maxTokens
    ) {
        ConverseRequest request = ConverseRequest.builder()
                .modelId(properties.modelId())
                .system(systemMessages)
                .messages(messages)
                .toolConfig(toolConfiguration)
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(properties.temperature())
                        .build())
                .build();
        return bedrockRuntimeClient.converse(request);
    }

    public ToolUseBlock extractToolUse(ConverseResponse response, String expectedToolName) {
        if (response.stopReason() != StopReason.TOOL_USE) {
            throw new ExternalApiException(
                    "Bedrock 응답이 tool_use가 아닙니다. stopReason=" + response.stopReason()
                            + ", expected=" + expectedToolName);
        }
        return response.output().message().content().stream()
                .filter(content -> content.toolUse() != null)
                .map(ContentBlock::toolUse)
                .filter(toolUse -> expectedToolName.equals(toolUse.name()))
                .findFirst()
                .orElseThrow(() -> new ExternalApiException(
                        "Bedrock 응답에 tool_use 블록이 없습니다. expected=" + expectedToolName));
    }

    public <T> T parseToolInput(ToolUseBlock toolUse, Class<T> type) {
        try {
            Object javaObject = DocumentJsonConverter.toJavaObject(toolUse.input());
            return objectMapper.convertValue(javaObject, type);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock toolUse 파싱 실패: input=" + toolUse.input(), e);
        }
    }
}
