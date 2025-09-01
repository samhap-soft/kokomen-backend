package com.samhap.kokomen.token.dto;

import jakarta.validation.constraints.NotNull;

public record TokenRefundRequest(
        @NotNull(message = "reason은 null일 수 없습니다.")
        String reason
) {
}
