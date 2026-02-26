package com.samhap.kokomen.payment.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @NotBlank(message = "paymentKey는 필수값입니다.")
        @Size(max = 200, message = "paymentKey는 최대 200자입니다.")
        String paymentKey,

        @NotBlank(message = "cancelReason은 필수값입니다.")
        @Size(max = 200, message = "cancelReason은 최대 200자입니다.")
        String cancelReason
) {
}
