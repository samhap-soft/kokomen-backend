package com.samhap.kokomen.token.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.service.dto.ConfirmRequest;
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

    public ConfirmRequest toPaymentConfirmRequest(Long memberId, ObjectMapper objectMapper) {
        TokenProduct product = readTokenProduct(productName);
        PurchaseMetadata metadata = new PurchaseMetadata(
                productName,
                getTokenCountFromProduct(product),
                product.getUnitPrice()
        );

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("metadata 직렬화 중 오류가 발생했습니다.", e);
        }

        return new ConfirmRequest(
                paymentKey,
                orderId,
                price,
                orderName,
                memberId,
                metadataJson,
                ServiceType.INTERVIEW
        );
    }

    public TokenPurchase toTokenPurchase(Long memberId, String paymentMethod, String easyPayProvider) {
        TokenProduct product = readTokenProduct(productName);
        return TokenPurchase.builder()
                .memberId(memberId)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(price)
                .orderName(orderName)
                .productName(productName)
                .purchaseCount(getTokenCountFromProduct(product))
                .unitPrice(product.getUnitPrice())
                .paymentMethod(paymentMethod)
                .easyPayProvider(easyPayProvider)
                .build();
    }

    private int getTokenCountFromProduct(TokenProduct product) {
        return product.getTokenCount();
    }

    private static TokenProduct readTokenProduct(String productName) {
        try {
            return TokenProduct.valueOf(productName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("유효하지 않은 product_name 입니다.");
        }
    }
}
