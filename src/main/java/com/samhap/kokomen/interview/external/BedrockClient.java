package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@Slf4j
@RequiredArgsConstructor
@Component
public class BedrockClient {

    private static final String INFERENCE_PROFILE_ID = "apac.anthropic.claude-3-sonnet-20240229-v1:0";

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    public BedrockResponse requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        ConverseRequest converseRequest = createConverseRequest(questionAndAnswers);
        ConverseResponse converseResponse = bedrockRuntimeClient.converse(converseRequest);

        String rawText = converseResponse.output().message().content().get(0).text();
        log.info("Bedrock API 원본 응답: {}", rawText);

        String cleanedContent = cleanJsonContent(rawText);
        log.info("정리된 JSON: {}", cleanedContent);

        return new BedrockResponse(cleanedContent);
    }

    private String cleanJsonContent(String rawText) {
        return rawText
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .replaceAll("`", "")
                .trim();
    }

    private ConverseRequest createConverseRequest(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = InterviewMessagesFactory.createBedrockMessages(questionAndAnswers);

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(INFERENCE_PROFILE_ID)
                .messages(messages);

        if (questionAndAnswers.isProceedRequest()) {
            builder.system(InterviewMessagesFactory.createBedrockProceedSystemMessage());
            return builder.build();
        }

        builder.system(InterviewMessagesFactory.createBedrockEndSystemMessage());
        return builder.build();
    }
}
