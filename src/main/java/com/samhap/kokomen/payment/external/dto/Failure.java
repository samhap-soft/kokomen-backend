package com.samhap.kokomen.payment.external.dto;

public record Failure(
        String code,
        String message
) {
}
