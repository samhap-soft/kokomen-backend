package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.request.Message;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GptClient {

    private static final String GPT_API_URL = "/v1/chat/completions";

    private final RestClient restClient;
    private final String gptApiKey;

    public GptClient(
            RestClient.Builder builder,
            @Value("${open-ai.api-key}") String gptApiKey
    ) {
        this.restClient = builder
                .baseUrl("https://api.openai.com")
                .build();
        this.gptApiKey = gptApiKey;
    }

    public GptResponse requestToGpt(QuestionAndAnswers questionAndAnswers) {
        GptRequest gptRequest = createGptRequest(questionAndAnswers);

        return restClient.post()
                .uri(GPT_API_URL)
                .header("Authorization", "Bearer " + gptApiKey)
                .body(gptRequest)
                .retrieve()
                .body(GptResponse.class);
    }

    private GptRequest createGptRequest(QuestionAndAnswers questionAndAnswers) {
        if (questionAndAnswers.isProceedRequest()) {
            List<Message> messages = InterviewMessagesFactory.createProceedMessages(questionAndAnswers);
            return GptRequest.createProceedGptRequest(messages);
        }
        List<Message> messages = InterviewMessagesFactory.createEndMessages(questionAndAnswers);
        return GptRequest.createEndGptRequest(messages);
    }
}
