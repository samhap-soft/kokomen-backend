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
        ConverseRequest converseRequest = createConverseRequest(questionAndAnswers);
        ConverseResponse converseResponse = bedrockRuntimeClient.converse(converseRequest);

        String content = converseResponse.output().message().content().get(0).text();
        return new BedrockResponse(content);
    }

    private ConverseRequest createConverseRequest(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = InterviewMessagesFactory.createBedrockMessages(questionAndAnswers);

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(MODEL_ID)
                .messages(messages);

        if (questionAndAnswers.isProceedRequest()) {
            builder.system(InterviewMessagesFactory.createBedrockProceedSystemMessage());
            return builder.build();
        }

        builder.system(InterviewMessagesFactory.createBedrockEndSystemMessage());
        return builder.build();
    }
}
