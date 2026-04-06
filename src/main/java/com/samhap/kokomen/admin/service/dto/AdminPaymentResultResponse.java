package com.samhap.kokomen.admin.service.dto;

import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import java.time.LocalDateTime;

public record AdminPaymentResultResponse(
        String method,
        Long balanceAmount,
        TosspaymentsStatus tosspaymentsStatus,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        String cancelReason,
        LocalDateTime canceledAt,
        String cancelStatus,
        String receiptUrl,
        String easyPayProvider
) {
    public static AdminPaymentResultResponse from(TosspaymentsPaymentResult result) {
        return new AdminPaymentResultResponse(
                result.getMethod(),
                result.getBalanceAmount(),
                result.getTosspaymentsStatus(),
                result.getRequestedAt(),
                result.getApprovedAt(),
                result.getCancelReason(),
                result.getCanceledAt(),
                result.getCancelStatus(),
                result.getReceiptUrl(),
                result.getEasyPayProvider()
        );
    }
}
