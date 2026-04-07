package com.samhap.kokomen.admin.service.dto;

import com.samhap.kokomen.payment.service.dto.CancelRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelPaymentRequest(
        @NotBlank(message = "cancelReason은 필수값입니다.")
        @Size(max = 200, message = "cancelReason은 최대 200자입니다.")
        String cancelReason
) {
    public CancelRequest toCancelRequest(String paymentKey) {
        return new CancelRequest(paymentKey, cancelReason);
    }
}
