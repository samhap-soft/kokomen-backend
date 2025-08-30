package com.samhap.kokomen.token.dto;

public record RefundRequest(
        String paymentKey,
        String reason
) {
}
