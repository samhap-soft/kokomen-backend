package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@RequiredArgsConstructor
@Component
public class BedrockClient {

    private static final String MODEL_ID = "anthropic.claude-3-sonnet-20240229-v1:0";

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public BedrockResponse requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        List<Message> bedrockMessages = createBedrockMessages(questionAndAnswers);

        ConverseRequest converseRequest = ConverseRequest.builder()
                .modelId(MODEL_ID)
                .messages(bedrockMessages)
                .build();

        ConverseResponse converseResponse = bedrockRuntimeClient.converse(converseRequest);

        String content = converseResponse.output().message().content().get(0).text();
        return new BedrockResponse(content);
    }

    private List<Message> createBedrockMessages(QuestionAndAnswers questionAndAnswers) {
        if (questionAndAnswers.isProceedRequest()) {
            return InterviewMessagesFactory.createBedrockProceedMessages(questionAndAnswers);
        }
        return InterviewMessagesFactory.createBedrockEndMessages(questionAndAnswers);
    }
} 
