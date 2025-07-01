package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.BedrockResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@Slf4j
@RequiredArgsConstructor
@Component
public class BedrockClient {

    private static final String INFERENCE_PROFILE_ID = "apac.anthropic.claude-sonnet-4-20250514-v1:0";

    private final BedrockRuntimeClient bedrockRuntimeClient;

    public BedrockResponse requestToBedrock(QuestionAndAnswers questionAndAnswers) {
        ConverseRequest converseRequest = createConverseRequest(questionAndAnswers);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        ConverseResponse converseResponse = bedrockRuntimeClient.converse(converseRequest);

        stopWatch.stop();
        log.info("Bedrock API 호출 완료 - {}ms", stopWatch.getTotalTimeMillis());

        String rawText = converseResponse.output().message().content().get(0).text();
        String cleanedContent = cleanJsonContent(rawText);

        return new BedrockResponse(cleanedContent);
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

    private String cleanJsonContent(String rawText) {
        return rawText
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .replaceAll("`", "")
                .trim();
    }
}
