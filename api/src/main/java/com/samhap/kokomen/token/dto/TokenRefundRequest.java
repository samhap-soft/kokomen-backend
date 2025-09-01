package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.token.domain.RefundReasonCode;
import jakarta.validation.constraints.NotNull;

public record TokenRefundRequest(
        @NotNull(message = "refundReasonCode는 null일 수 없습니다.")
        RefundReasonCode refundReasonCode,
        String refundReasonText
) {
}
