package com.samhap.kokomen.token.dto;

import jakarta.validation.constraints.NotNull;

public record TokenRefundRequest(
        @NotNull(message = "token_purchase_id는 null일 수 없습니다.")
        Long tokenPurchaseId,
        @NotNull(message = "reason은 null일 수 없습니다.")
        String reason
) {
}
