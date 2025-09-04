package com.samhap.kokomen.product.service.dto;

import com.samhap.kokomen.product.domain.TokenProduct;

public record ProductResponse(
        String orderName,
        String productName,
        Long price
) {
    public ProductResponse(TokenProduct product) {
        this(
                product.getOrderName(),
                product.name(),
                product.getPrice()
        );
    }
}