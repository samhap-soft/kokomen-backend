package com.samhap.kokomen.token.domain;

import lombok.Getter;

@Getter
public enum TokenPrice {
    SINGLE_TOKEN(1, 30L);

    private final int tokenCount;
    private final long price;

    TokenPrice(int tokenCount, long price) {
        this.tokenCount = tokenCount;
        this.price = price;
    }

    public static boolean isValidPrice(int tokenCount, long totalAmount) {
        return calculatePrice(tokenCount) == totalAmount;
    }

    public static long calculatePrice(int tokenCount) {
        return tokenCount * SINGLE_TOKEN.price;
    }
}
