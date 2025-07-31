package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@ExecutionTimer
@RequiredArgsConstructor
@Component
public class BedrockAsyncClient {

    private static final List<String> MODEL_IDS = List.of(
            "apac.anthropic.claude-3-sonnet-20240229-v1:0",
            "apac.anthropic.claude-3-5-sonnet-20240620-v1:0",
            "apac.anthropic.claude-3-5-sonnet-20241022-v2:0",
            "apac.anthropic.claude-3-7-sonnet-20250219-v1:0",
            "apac.anthropic.claude-sonnet-4-20250514-v1:0"
    );

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

    public CompletableFuture<ConverseResponse> requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        ConverseRequest converseRequest = createConverseRequest(questionAndAnswers);
        return bedrockRuntimeAsyncClient.converse(converseRequest);
    }

    private ConverseRequest createConverseRequest(QuestionAndAnswers questionAndAnswers) {
        int index = COUNTER.getAndIncrement() % MODEL_IDS.size();
        List<Message> messages = InterviewMessagesFactory.createBedrockMessages(questionAndAnswers);

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(MODEL_IDS.get(index))
                .messages(messages);

        if (questionAndAnswers.isProceedRequest()) {
            builder.system(InterviewMessagesFactory.createBedrockProceedSystemMessage());
            return builder.build();
        }

        builder.system(InterviewMessagesFactory.createBedrockEndSystemMessage());
        return builder.build();
    }
}
