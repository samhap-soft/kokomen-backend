package com.samhap.kokomen.admin.service.dto;

import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import java.time.LocalDateTime;

public record AdminPaymentResponse(
        Long id,
        String paymentKey,
        Long memberId,
        String orderId,
        String orderName,
        Long totalAmount,
        String metadata,
        PaymentState state,
        ServiceType serviceType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AdminPaymentResultResponse result
) {
    public static AdminPaymentResponse of(TosspaymentsPayment payment, TosspaymentsPaymentResult result) {
        return new AdminPaymentResponse(
                payment.getId(),
                payment.getPaymentKey(),
                payment.getMemberId(),
                payment.getOrderId(),
                payment.getOrderName(),
                payment.getTotalAmount(),
                payment.getMetadata(),
                payment.getState(),
                payment.getServiceType(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                result != null ? AdminPaymentResultResponse.from(result) : null
        );
    }
}
