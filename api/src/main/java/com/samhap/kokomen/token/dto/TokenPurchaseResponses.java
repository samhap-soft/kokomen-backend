package com.samhap.kokomen.token.dto;

import java.util.List;

public record TokenPurchaseResponses(
        List<TokenPurchaseResponse> tokenPurchases,
        Long totalPageCount
) {
    public static TokenPurchaseResponses from(List<TokenPurchaseResponse> tokenPurchases, Long totalPageCount) {
        return new TokenPurchaseResponses(tokenPurchases, totalPageCount);
    }
}