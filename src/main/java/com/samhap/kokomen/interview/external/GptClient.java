package com.samhap.kokomen.interview.external;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.request.Message;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;

@Component
public class GptClient {

    private static final String GPT_API_URL = "/v1/chat/completions";
    private static final int MAX_ANSWER_COUNT = 3;

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

    public GptResponse requestToGpt(List<Answer> prevAnswers, Question curQuestion, String curAnswerContent) {
        GptRequest gptRequest = createGptRequest(prevAnswers, curQuestion, curAnswerContent);

        return restClient.post()
                .uri(GPT_API_URL)
                .header("Authorization", "Bearer " + gptApiKey)
                .body(gptRequest)
                .retrieve()
                .body(GptResponse.class);
    }

    private GptRequest createGptRequest(List<Answer> prevAnswers, Question curQuestion, String curAnswerContent) {
        if (isProceedRequest(prevAnswers)) {
            List<Message> messages = InterviewMessagesFactory.createProceedMessages(prevAnswers, curQuestion, curAnswerContent);
            return GptRequest.createProceedGptRequest(messages);
        }
        List<Message> messages = InterviewMessagesFactory.createEndMessages(prevAnswers, curQuestion, curAnswerContent);
        return GptRequest.createEndGptRequest(messages);
    }

    public boolean isProceedRequest(List<Answer> prevAnswers) {
        int totalAnswerCount = prevAnswers.size() + 1;
        return totalAnswerCount < MAX_ANSWER_COUNT;
    }
}
