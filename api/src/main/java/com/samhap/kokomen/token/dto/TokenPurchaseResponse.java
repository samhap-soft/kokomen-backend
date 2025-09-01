package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.token.domain.TokenPurchase;

public record TokenPurchaseResponse(
        Long id,
        Long totalAmount,
        String productName,
        Integer count,
        Integer remainingCount,
        String state,
        Long unitPrice
) {
    public static TokenPurchaseResponse from(TokenPurchase tokenPurchase) {
        return new TokenPurchaseResponse(
                tokenPurchase.getId(),
                tokenPurchase.getTotalAmount(),
                tokenPurchase.getProductName(),
                tokenPurchase.getPurchaseCount(),
                tokenPurchase.getRemainingCount(),
                tokenPurchase.getState().getDisplayMessage(),
                tokenPurchase.getUnitPrice()
        );
    }
}
