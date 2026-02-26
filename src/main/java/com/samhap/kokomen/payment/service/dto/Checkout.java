package com.samhap.kokomen.payment.service.dto;

public record Checkout(
        String url
) {

    public static Checkout from(com.samhap.kokomen.payment.external.dto.Checkout checkout) {
        return new Checkout(checkout.url());
    }
}
