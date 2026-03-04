package com.samhap.kokomen.token.dto;

import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;

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
                resolveDisplayState(tokenPurchase),
                tokenPurchase.getPaymentMethod(),
                tokenPurchase.getEasyPayProvider()
        );
    }

    private static String resolveDisplayState(TokenPurchase tokenPurchase) {
        if (tokenPurchase.getState() == TokenPurchaseState.REFUNDABLE && tokenPurchase.isRefundExpired()) {
            return TokenPurchaseState.REFUND_EXPIRED.getDisplayMessage();
        }
        return tokenPurchase.getState().getDisplayMessage();
    }
}
