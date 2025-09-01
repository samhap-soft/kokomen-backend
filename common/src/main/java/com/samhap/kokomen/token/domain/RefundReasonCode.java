package com.samhap.kokomen.token.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
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

    public String getRefundReason(String refundReasonText) {
        if (this == OTHER && refundReasonText != null && !refundReasonText.trim().isEmpty()) {
            return refundReasonText;
        }
        return this.message;
    }

    public void validateRefundReasonText(String refundReasonText) {
        if (this == OTHER) {
            if (refundReasonText == null || refundReasonText.trim().isEmpty()) {
                throw new BadRequestException("OTHER 선택 시 환불 사유를 입력해야 합니다.");
            }
        }

        if (refundReasonText != null && refundReasonText.length() > 200) {
            throw new BadRequestException("환불 사유는 200자를 초과할 수 없습니다.");
        }
    }

    public boolean requiresReasonText() {
        return this == OTHER;
    }
}
