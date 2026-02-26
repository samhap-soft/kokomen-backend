package com.samhap.kokomen.payment.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TossDateTimeDeserializer;
import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record WebhookPaymentData(
        String paymentKey,
        String orderId,
        String orderName,
        String mId,
        String currency,
        String method,
        Long totalAmount,
        Long balanceAmount,
        TosspaymentsStatus status,
        @JsonDeserialize(using = TossDateTimeDeserializer.class)
        LocalDateTime requestedAt,
        @JsonDeserialize(using = TossDateTimeDeserializer.class)
        LocalDateTime approvedAt,
        String lastTransactionKey,
        Long suppliedAmount,
        Long vat,
        Long taxFreeAmount,
        Long taxExemptionAmount,
        boolean isPartialCancelable,
        WebhookEasyPay easyPay,
        String country
) {

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record WebhookEasyPay(
            String provider,
            Long amount,
            Long discountAmount
    ) {
    }
}
