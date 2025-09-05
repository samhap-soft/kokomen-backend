package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.TokenPurchase;

public record TokenPurchaseResponse(
        Long id,
        Long price,
        String orderName,
        String productName,
        Integer count,
        Integer remainingCount,
        String state,
        String paymentMethod,
        String easyPayProvider
) {
    public static TokenPurchaseResponse from(TokenPurchase tokenPurchase) {
        TokenProduct product = TokenProduct.valueOf(tokenPurchase.getProductName());
        return new TokenPurchaseResponse(
                tokenPurchase.getId(),
                tokenPurchase.getTotalAmount(),
                product.getOrderName(),
                tokenPurchase.getProductName(),
                tokenPurchase.getPurchaseCount(),
                tokenPurchase.getRemainingCount(),
                tokenPurchase.getState().getDisplayMessage(),
                tokenPurchase.getPaymentMethod(),
                tokenPurchase.getEasyPayProvider()
        );
    }
}
