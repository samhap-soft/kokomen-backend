package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisBedrockRequestFactory;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisEvaluationFlatResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class ResumeAnalysisEvaluationBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public ResumeAnalysisEvaluationBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public ResumeAnalysisEvaluation evaluate(ResumeAnalysisCommand command) {
        ConverseResponse response = converseClient.converse(
                ResumeAnalysisBedrockRequestFactory.createEvaluationSystem(command.jdProvided()),
                ResumeAnalysisBedrockRequestFactory.createEvaluationMessages(command),
                ResumeAnalysisBedrockRequestFactory.createEvaluationToolConfig(command.jdProvided()),
                properties.resumeEvaluationMaxTokens(),
                properties.evaluationTemperature());

        ToolUseBlock toolUse = converseClient.extractToolUse(response, ResumeAnalysisToolNames.EVALUATION);
        return converseClient.parseToolInput(toolUse, ResumeAnalysisEvaluationFlatResponse.class)
                .toEvaluation(command.jdProvided());
    }
}
