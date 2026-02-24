package com.samhap.kokomen.product.domain;

import java.util.List;
import lombok.Getter;

@Getter
public enum TokenProduct {

    TOKEN_10("토큰 10개", 10, 50L),
    TOKEN_20("토큰 20개", 20, 50L),
    TOKEN_50("토큰 50개", 50, 50L),
    TOKEN_100("토큰 100개", 100, 50L),
    TOKEN_200("토큰 200개", 200, 50L);

    private final String orderName;
    private final int tokenCount;
    private final long unitPrice;

    TokenProduct(String orderName, int tokenCount, long unitPrice) {
        this.orderName = orderName;
        this.tokenCount = tokenCount;
        this.unitPrice = unitPrice;
    }

    public long getPrice() {
        return tokenCount * unitPrice;
    }

    private static final List<TokenProduct> PRODUCTS = List.of(values());

    public static List<TokenProduct> getProducts() {
        return PRODUCTS;
    }
}
