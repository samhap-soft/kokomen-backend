package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.external.dto.request.SupertoneRequest;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@ExecutionTimer
@Component
public class SupertoneClient {

    //    private static final String VOICE_ID = "xkWjpC3HjjTG6NdLNGMNd6";
    private static final String VOICE_ID = "838617ea6b672b84de0813";

    private final RestClient restClient;

    public SupertoneClient(SupertoneClientBuilder supertoneClientBuilder) {
        this.restClient = supertoneClientBuilder.getSupertoneClientBuilder().build();
    }

    public SupertoneResponse request(SupertoneRequest supertoneRequest) {
        try {
            byte[] voiceData = restClient.post()
                    .uri("/v1/text-to-speech/" + VOICE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(supertoneRequest)
                    .retrieve()
                    .body(byte[].class);

            return new SupertoneResponse(voiceData);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("Supertone API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new ExternalApiException("Supertone API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }

    public SupertoneResponse requestWithApiKey(SupertoneRequest supertoneRequest, String apiKey) {
        try {
            byte[] voiceData = restClient.post()
                    .uri("/v1/text-to-speech/" + VOICE_ID)
                    .header("x-sup-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(supertoneRequest)
                    .retrieve()
                    .body(byte[].class);

            return new SupertoneResponse(voiceData);
        } catch (RestClientResponseException e) {
            throw new ExternalApiException("Supertone API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new ExternalApiException("Supertone API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }
}
