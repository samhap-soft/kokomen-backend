package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.resume.external.dto.ResumeBedrockRequestFactory;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class ResumeEvaluationBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public ResumeEvaluationBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public ResumeEvaluationResponse evaluate(ResumeEvaluationRequest request) {
        ConverseResponse response = converseClient.converse(
                ResumeBedrockRequestFactory.createEvaluationSystem(),
                ResumeBedrockRequestFactory.createEvaluationMessages(request),
                ResumeBedrockRequestFactory.createEvaluationToolConfig(),
                properties.resumeEvaluationMaxTokens(),
                properties.evaluationTemperature());

        ToolUseBlock toolUse = converseClient.extractToolUse(response,
                ResumeBedrockRequestFactory.EVALUATION_TOOL_NAME);
        return converseClient.parseToolInput(toolUse, ResumeEvaluationResponse.class);
    }
}
