package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.LlmApiException;
import com.samhap.kokomen.interview.domain.InterviewMessagesFactory;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import com.samhap.kokomen.interview.external.dto.request.GptRequest;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

        GptResponse gptResponse;
        try {
            gptResponse = restClient.post()
                    .uri(GPT_API_URL)
                    .header("Authorization", "Bearer " + gptApiKey)
                    .body(gptRequest)
                    .retrieve()
                    .body(GptResponse.class);
        } catch (RestClientResponseException e) {
            throw new LlmApiException("GPT API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new LlmApiException("GPT API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
        stopWatch.stop();
        log.info("GPT API 호출 완료 - {}ms", stopWatch.getTotalTimeMillis());

        return gptResponse;
    }

    private GptRequest createGptRequest(QuestionAndAnswers questionAndAnswers) {
        if (questionAndAnswers.isProceedRequest()) {
            List<GptMessage> gptMessages = InterviewMessagesFactory.createGptProceedMessages(questionAndAnswers);
            return GptRequest.createProceedGptRequest(gptMessages);
        }
        List<GptMessage> gptMessages = InterviewMessagesFactory.createGptEndMessages(questionAndAnswers);
        return GptRequest.createEndGptRequest(gptMessages);
    }
}
