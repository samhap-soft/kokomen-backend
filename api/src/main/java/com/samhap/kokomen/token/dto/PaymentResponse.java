package com.samhap.kokomen.token.dto;

public record PaymentResponse(
        String method,
        EasyPay easyPay
) {
    public record EasyPay(
            String provider
    ) {
    }
}