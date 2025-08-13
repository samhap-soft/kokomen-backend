package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.external.dto.request.TypecastRequest;
import com.samhap.kokomen.interview.external.dto.response.TypecastResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@ExecutionTimer
@Component
public class TypecastClient {

    private final RestClient restClient;

    public TypecastClient(TypecastClientBuilder typecastClientBuilder) {
        this.restClient = typecastClientBuilder.getTypecastClientBuilder().build();
    }

    public TypecastResponse request(TypecastRequest typecastRequest) {
        try {
            return restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(typecastRequest)
                    .retrieve()
                    .body(TypecastResponse.class);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("Typecast API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new ExternalApiException("Typecast API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }
}
