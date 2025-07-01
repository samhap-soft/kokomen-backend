package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.request.Message;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class GptClient {

    private static final String GPT_API_URL = "/v1/chat/completions";

    private final RestClient restClient;
    private final String gptApiKey;

    public GptClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${open-ai.api-key}") String gptApiKey
    ) {
        this.restClient = builder
                .baseUrl("https://api.openai.com")
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
        this.gptApiKey = gptApiKey;
    }

    public GptResponse requestToGpt(QuestionAndAnswers questionAndAnswers) {
        GptRequest gptRequest = createGptRequest(questionAndAnswers);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        GptResponse gptResponse = restClient.post()
                .uri(GPT_API_URL)
                .header("Authorization", "Bearer " + gptApiKey)
                .body(gptRequest)
                .retrieve()
                .body(GptResponse.class);

        stopWatch.stop();
        log.info("GPT API 호출 완료 - {}ms", stopWatch.getTotalTimeMillis());

        return gptResponse;
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
