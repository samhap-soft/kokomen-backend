package com.samhap.kokomen.token.domain;

import lombok.Getter;

@Getter
public enum RefundReasonCode {
    CHANGE_OF_MIND("단순 변심"),
    NO_LONGER_USING_SERVICE("서비스를 더 이상 이용하지 않음"),
    NOT_AS_EXPECTED("기대했던 서비스와 다름"),
    SERVICE_DISSATISFACTION("서비스에 불만족"),
    TECHNICAL_ISSUE("기술적 문제 또는 버그"),
    OTHER("기타");

    private final String message;

    RefundReasonCode(String message) {
        this.message = message;
    }

    public boolean requiresReasonText() {
        return this == OTHER;
    }

    public String getRefundReason(String refundReasonText) {
        if (this == OTHER && refundReasonText != null && !refundReasonText.trim().isEmpty()) {
            return refundReasonText;
        }
        return this.message;
    }
}
