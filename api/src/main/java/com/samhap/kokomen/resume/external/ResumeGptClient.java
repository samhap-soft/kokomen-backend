package com.samhap.kokomen.resume.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.resume.external.dto.ResumeGptRequest;
import com.samhap.kokomen.resume.external.dto.ResumeGptResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@ExecutionTimer
@Component
public class ResumeGptClient {

    private static final String GPT_API_URL = "/v1/chat/completions";

    private final RestClient restClient;
    private final String gptApiKey;

    public ResumeGptClient(
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

    public String requestResumeEvaluation(ResumeEvaluationRequest request) {
        ResumeGptRequest gptRequest = ResumeGptRequest.create(request);
        ResumeGptResponse gptResponse;
        try {
            gptResponse = restClient.post()
                    .uri(GPT_API_URL)
                    .header("Authorization", "Bearer " + gptApiKey)
                    .body(gptRequest)
                    .retrieve()
                    .body(ResumeGptResponse.class);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("GPT API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new ExternalApiException("GPT API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }

        if (gptResponse == null || gptResponse.choices() == null || gptResponse.choices().isEmpty()) {
            throw new ExternalApiException("GPT API로부터 유효한 응답을 받지 못했습니다.");
        }

        return gptResponse.choices().get(0).message().content();
    }
}
