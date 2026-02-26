package com.samhap.kokomen.payment.external.dto;

import java.util.Objects;

public record TosspaymentsPaymentCancelRequest(
        String cancelReason
) {

    public TosspaymentsPaymentCancelRequest {
        Objects.requireNonNull(cancelReason, "cancelReason은 필수입니다.");
        if (cancelReason.isBlank()) {
            throw new IllegalArgumentException("cancelReason은 비어있을 수 없습니다.");
        }
    }
}
