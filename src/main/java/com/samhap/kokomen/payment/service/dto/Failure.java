package com.samhap.kokomen.payment.service.dto;

public record Failure(
        String code,
        String message
) {

    public static Failure from(com.samhap.kokomen.payment.external.dto.Failure failure) {
        return new Failure(failure.code(), failure.message());
    }
}
