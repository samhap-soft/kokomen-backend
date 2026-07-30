package com.samhap.kokomen.resume.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisBedrockRequestFactory;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionResult;
import com.samhap.kokomen.resume.external.dto.ResumeAnalysisQuestionsFlatResponse;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class ResumeAnalysisQuestionBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public ResumeAnalysisQuestionBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public ResumeAnalysisQuestionResult generateQuestions(ResumeAnalysisQuestionCallCommand command) {
        ConverseResponse response = converseClient.converse(
                ResumeAnalysisBedrockRequestFactory.createQuestionGenerationSystem(),
                ResumeAnalysisBedrockRequestFactory.createQuestionGenerationMessages(command),
                ResumeAnalysisBedrockRequestFactory.createQuestionGenerationToolConfig(),
                properties.resumeQuestionMaxTokens(),
                properties.generationTemperature());

        ToolUseBlock toolUse = converseClient.extractToolUse(response,
                ResumeAnalysisToolNames.QUESTION_GENERATION);
        return converseClient.parseToolInput(toolUse, ResumeAnalysisQuestionsFlatResponse.class).toResult();
    }
}
