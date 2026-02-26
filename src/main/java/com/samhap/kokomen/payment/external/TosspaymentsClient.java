package com.samhap.kokomen.payment.external;

import com.samhap.kokomen.payment.external.dto.TosspaymentsConfirmRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentCancelRequest;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TosspaymentsClient {

    private final RestClient restClient;

    public TosspaymentsClient(TossPaymentsClientBuilder tossPaymentsClientBuilder) {
        this.restClient = tossPaymentsClientBuilder.getTossPaymentsClientBuilder().build();
    }

    public TosspaymentsPaymentResponse confirmPayment(TosspaymentsConfirmRequest request, String idempotencyKey) {
        return restClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TosspaymentsPaymentResponse.class);
    }

    public TosspaymentsPaymentResponse cancelPayment(
            String paymentKey,
            TosspaymentsPaymentCancelRequest request
    ) {
        return restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(request)
                .retrieve()
                .body(TosspaymentsPaymentResponse.class);
    }
}
