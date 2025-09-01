package com.samhap.kokomen.token.dto;

public record ConfirmRequest(
        String paymentKey,
        String orderId,
        Long totalAmount,
        String orderName,
        Long memberId,
        PurchaseMetadata metadata,
        String serviceType
) {
}
