package com.samhap.kokomen.token.external;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.exception.InternalApiException;
import com.samhap.kokomen.token.dto.ConfirmRequest;
import com.samhap.kokomen.token.dto.PaymentResponse;
import com.samhap.kokomen.token.dto.RefundRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@ExecutionTimer
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(PaymentClientBuilder paymentClientBuilder) {
        this.restClient = paymentClientBuilder.getPaymentClientBuilder().build();
    }

    public PaymentResponse confirmPayment(ConfirmRequest confirmRequest) {
        try {
            return restClient.post()
                    .uri("/internal/v1/payments/confirm")
                    .body(confirmRequest)
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (RestClientResponseException e) {
            throw new InternalApiException("Payment API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new InternalApiException("Payment API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }

    public void refundPayment(RefundRequest refundRequest) {
        try {
            restClient.post()
                    .uri("/internal/v1/payments/cancel")
                    .body(refundRequest)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new InternalApiException("Payment API 서버로부터 오류 응답을 받았습니다. 상태 코드: " + e.getRawStatusCode(), e);
        } catch (Exception e) {
            throw new InternalApiException("Payment API 호출 중 예상치 못한 오류가 발생했습니다.", e);
        }
    }
}
