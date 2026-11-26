package com.samhap.kokomen.payment.external.dto;

import java.time.LocalDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

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
