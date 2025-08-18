package com.samhap.kokomen.interview.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.InternalApiException;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@ExecutionTimer
@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(NotificationClientBuilder notificationClientBuilder) {
        this.restClient = notificationClientBuilder.getNotificationClientBuilder().build();
    }

    public void request(NotificationRequest notificationRequest) {
        try {
            restClient.post()
                    .uri("/internal/v1/notifications")
                    .body(notificationRequest)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new InternalApiException("Notification API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new InternalApiException("Notification API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }
}
