package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.token.domain.TokenPurchase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TokenPurchaseRequest(
        @NotBlank(message = "payment_key는 비어있거나 공백일 수 없습니다.")
        String paymentKey,
        @NotBlank(message = "order_id는 비어있거나 공백일 수 없습니다.")
        String orderId,
        @NotNull(message = "total_amount는 null일 수 없습니다.")
        @Positive(message = "total_amount는 양수여야 합니다.")
        Long totalAmount,
        @NotBlank(message = "order_name은 비어있거나 공백일 수 없습니다.")
        String orderName,
        @Valid
        @NotNull(message = "metadata는 null일 수 없습니다.")
        PurchaseMetadata metadata
) {

    public ConfirmRequest toConfirmRequest(Long memberId) {
        return new ConfirmRequest(
                paymentKey,
                orderId,
                totalAmount,
                orderName,
                memberId,
                metadata,
                "INTERVIEW"
        );
    }

    public TokenPurchase toTokenPurchase(Long memberId) {
        return TokenPurchase.builder()
                .memberId(memberId)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .orderName(orderName)
                .productName(metadata.productName())
                .purchaseCount(metadata.count())
                .unitPrice(metadata.unitPrice())
                .paymentMethod("Unknown")
                .easyPayProvider(null)
                .build();
    }

    public TokenPurchase toTokenPurchase(Long memberId, PaymentResponse paymentResponse) {
        return TokenPurchase.builder()
                .memberId(memberId)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .orderName(orderName)
                .productName(metadata.productName())
                .purchaseCount(metadata.count())
                .unitPrice(metadata.unitPrice())
                .paymentMethod(paymentResponse.method())
                .easyPayProvider(paymentResponse.easyPay() != null ? paymentResponse.easyPay().provider() : null)
                .build();
    }
}
