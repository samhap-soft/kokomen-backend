package com.samhap.kokomen.payment.external.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;

public record TosspaymentsCancel(
        String transactionKey,
        String cancelReason,
        Long taxExemptionAmount,
        @JsonDeserialize(using = TossDateTimeDeserializer.class)
        LocalDateTime canceledAt,
        Long easyPayDiscountAmount,
        String receiptKey,
        Long cancelAmount,
        Long taxFreeAmount,
        Long refundableAmount,
        String cancelStatus,
        String cancelRequestId
) {
}
