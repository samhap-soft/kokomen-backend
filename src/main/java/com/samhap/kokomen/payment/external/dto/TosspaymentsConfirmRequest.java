package com.samhap.kokomen.payment.external.dto;

import java.util.Objects;

public record TosspaymentsConfirmRequest(
        String paymentKey,
        String orderId,
        Long amount
) {

    public TosspaymentsConfirmRequest {
        Objects.requireNonNull(paymentKey, "paymentKey는 필수입니다.");
        Objects.requireNonNull(orderId, "orderId는 필수입니다.");
        Objects.requireNonNull(amount, "amount는 필수입니다.");
    }
}
