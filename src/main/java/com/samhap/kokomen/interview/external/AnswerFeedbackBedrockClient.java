package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.interview.external.dto.request.InterviewBedrockRequestFactory;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackOnlyResponse;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
@ExecutionTimer
@Component
public class AnswerFeedbackBedrockClient {

    private final BedrockConverseClient converseClient;
    private final BedrockConverseProperties properties;

    public AnswerFeedbackBedrockClient(
            BedrockConverseClient converseClient,
            BedrockConverseProperties properties
    ) {
        this.converseClient = converseClient;
        this.properties = properties;
    }

    public String requestAnswerFeedback(QuestionAndAnswers questionAndAnswers, AnswerRank curAnswerRank) {
        ConverseResponse response = converseClient.converse(
                InterviewBedrockRequestFactory.createAnswerFeedbackSystem(curAnswerRank),
                InterviewBedrockRequestFactory.createAnswerFeedbackMessages(questionAndAnswers),
                InterviewBedrockRequestFactory.createAnswerFeedbackToolConfig(),
                properties.answerFeedbackMaxTokens(),
                properties.feedbackTemperature());
        ToolUseBlock toolUse = converseClient.extractToolUse(response, InterviewBedrockRequestFactory.ANSWER_FEEDBACK_TOOL_NAME);
        AnswerFeedbackOnlyResponse parsed = converseClient.parseToolInput(toolUse, AnswerFeedbackOnlyResponse.class);
        if (parsed.feedback() == null || parsed.feedback().isBlank()) {
            throw new ExternalApiException("Bedrock 답변 피드백 응답이 비어있습니다.");
        }
        return parsed.feedback();
    }
}
