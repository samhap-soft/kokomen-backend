package com.samhap.kokomen.global.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public abstract class BaseGptClient {

    protected static final String GPT_API_URL = "/v1/chat/completions";
    protected static final String GPT_BASE_URL = "https://api.openai.com";

    protected final RestClient restClient;
    protected final String gptApiKey;

    protected BaseGptClient(RestClient.Builder builder, ObjectMapper objectMapper, String gptApiKey) {
        this.restClient = builder
                .baseUrl(GPT_BASE_URL)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
        this.gptApiKey = gptApiKey;
    }

    protected <T> T executeRequest(Object request, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(GPT_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + gptApiKey)
                    .body(request)
                    .retrieve()
                    .body(responseType);
            validateResponse(response);
            return response;
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("GPT API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getStatusCode(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GPT API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }

    protected abstract void validateResponse(Object response);
}
