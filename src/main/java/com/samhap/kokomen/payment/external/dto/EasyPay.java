package com.samhap.kokomen.payment.external.dto;

public record EasyPay(
        String provider,
        Long amount,
        Long discountAmount
) {
}
