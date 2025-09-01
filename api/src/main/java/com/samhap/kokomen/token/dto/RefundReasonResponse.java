package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.token.domain.RefundReasonCode;

public record RefundReasonResponse(
        String code,
        String message,
        boolean requiresReasonText
) {
    public static RefundReasonResponse from(RefundReasonCode refundReasonCode) {
        return new RefundReasonResponse(
                refundReasonCode.name(),
                refundReasonCode.getMessage(),
                refundReasonCode.requiresReasonText()
        );
    }
}
