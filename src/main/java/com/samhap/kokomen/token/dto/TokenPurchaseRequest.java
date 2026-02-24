package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.TokenPurchase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TokenPurchaseRequest(
        @NotBlank(message = "payment_key는 비어있거나 공백일 수 없습니다.")
        String paymentKey,
        @NotBlank(message = "order_id는 비어있거나 공백일 수 없습니다.")
        String orderId,
        @NotNull(message = "price는 null일 수 없습니다.")
        @Positive(message = "price는 양수여야 합니다.")
        Long price,
        @NotBlank(message = "order_name은 비어있거나 공백일 수 없습니다.")
        String orderName,
        @NotBlank(message = "product_name은 비어있거나 공백일 수 없습니다.")
        String productName
) {

    public ConfirmRequest toConfirmRequest(Long memberId) {
        TokenProduct product = TokenProduct.valueOf(productName);
        PurchaseMetadata metadata = new PurchaseMetadata(
                productName,
                getTokenCountFromProduct(product),
                product.getUnitPrice()
        );

        return new ConfirmRequest(
                paymentKey,
                orderId,
                price,
                orderName,
                memberId,
                metadata,
                "INTERVIEW"
        );
    }

    public TokenPurchase toTokenPurchase(Long memberId, PaymentResponse paymentResponse) {
        TokenProduct product = TokenProduct.valueOf(productName);
        return TokenPurchase.builder()
                .memberId(memberId)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(price)
                .orderName(orderName)
                .productName(productName)
                .purchaseCount(getTokenCountFromProduct(product))
                .unitPrice(product.getUnitPrice())
                .paymentMethod(paymentResponse.method())
                .easyPayProvider(paymentResponse.easyPay() != null ? paymentResponse.easyPay().provider() : null)
                .build();
    }

    private int getTokenCountFromProduct(TokenProduct product) {
        return product.getTokenCount();
    }
}
