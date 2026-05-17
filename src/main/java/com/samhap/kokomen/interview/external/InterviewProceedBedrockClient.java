package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.external.dto.request.InterviewBedrockRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.BedrockConverseResponse;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class InterviewProceedBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public InterviewProceedBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public BedrockConverseResponse requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        if (questionAndAnswers.isProceedRequest()) {
            return requestProceed(questionAndAnswers);
        }
        return requestEnd(questionAndAnswers);
    }

    private BedrockConverseResponse requestProceed(QuestionAndAnswers questionAndAnswers) {
        ConverseResponse response = converseClient.converse(
                InterviewBedrockRequestFactory.createProceedSystem(),
                InterviewBedrockRequestFactory.createProceedMessages(questionAndAnswers),
                InterviewBedrockRequestFactory.createProceedToolConfig(),
                properties.proceedMaxTokens());
        ToolUseBlock toolUse = converseClient.extractToolUse(response, InterviewBedrockRequestFactory.PROCEED_TOOL_NAME);
        return new BedrockConverseResponse(toolUse.input());
    }

    private BedrockConverseResponse requestEnd(QuestionAndAnswers questionAndAnswers) {
        ConverseResponse response = converseClient.converse(
                InterviewBedrockRequestFactory.createEndSystem(),
                InterviewBedrockRequestFactory.createProceedMessages(questionAndAnswers),
                InterviewBedrockRequestFactory.createEndToolConfig(),
                properties.endMaxTokens());
        ToolUseBlock toolUse = converseClient.extractToolUse(response, InterviewBedrockRequestFactory.END_TOOL_NAME);
        return new BedrockConverseResponse(toolUse.input());
    }
}
