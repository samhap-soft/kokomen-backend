package com.samhap.kokomen.payment.service.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.samhap.kokomen.payment.external.dto.TossDateTimeDeserializer;
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

    public static TosspaymentsCancel from(com.samhap.kokomen.payment.external.dto.TosspaymentsCancel cancel) {
        return new TosspaymentsCancel(
                cancel.transactionKey(),
                cancel.cancelReason(),
                cancel.taxExemptionAmount(),
                cancel.canceledAt(),
                cancel.easyPayDiscountAmount(),
                cancel.receiptKey(),
                cancel.cancelAmount(),
                cancel.taxFreeAmount(),
                cancel.refundableAmount(),
                cancel.cancelStatus(),
                cancel.cancelRequestId()
        );
    }
}
