package com.samhap.kokomen.payment.service.dto;

public record Receipt(
        String url
) {

    public static Receipt from(com.samhap.kokomen.payment.external.dto.Receipt receipt) {
        return new Receipt(receipt.url());
    }
}
