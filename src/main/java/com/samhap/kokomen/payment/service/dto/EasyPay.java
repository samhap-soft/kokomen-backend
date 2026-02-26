package com.samhap.kokomen.payment.service.dto;

public record EasyPay(
        String provider,
        Long amount,
        Long discountAmount
) {

    public static EasyPay from(com.samhap.kokomen.payment.external.dto.EasyPay easyPay) {
        return new EasyPay(
                easyPay.provider(),
                easyPay.amount(),
                easyPay.discountAmount()
        );
    }
}
