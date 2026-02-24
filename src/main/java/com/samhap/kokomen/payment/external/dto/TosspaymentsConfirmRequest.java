package com.samhap.kokomen.payment.external.dto;

public record TosspaymentsConfirmRequest(
        String paymentKey,
        String orderId,
        Long amount
) {
}
