package com.samhap.kokomen.payment.domain;

import lombok.Getter;

@Getter
public enum PaymentErrorMessage {

    PAYMENT_KEY_MISMATCH("토스 페이먼츠 응답의 paymentKey가 DB에 저장된 값과 다릅니다."),
    ORDER_ID_MISMATCH("토스 페이먼츠 응답의 orderId가 DB에 저장된 값과 다릅니다."),
    TOTAL_AMOUNT_MISMATCH("토스 페이먼츠 응답의 totalAmount가 DB에 저장된 값과 다릅니다.");

    private final String message;

    PaymentErrorMessage(String message) {
        this.message = message;
    }
}
