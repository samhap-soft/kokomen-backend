package com.samhap.kokomen.global.fixture.token;

import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;

public class TokenPurchaseFixtureBuilder {

    private Long id;
    private Long memberId;
    private String paymentKey;
    private String orderId;
    private Long totalAmount;
    private String orderName;
    private String productName;
    private Integer purchaseCount;
    private Long unitPrice;
    private TokenPurchaseState state;
    private Integer remainingCount;

    public static TokenPurchaseFixtureBuilder builder() {
        return new TokenPurchaseFixtureBuilder();
    }

    public TokenPurchaseFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public TokenPurchaseFixtureBuilder memberId(Long memberId) {
        this.memberId = memberId;
        return this;
    }

    public TokenPurchaseFixtureBuilder paymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
        return this;
    }

    public TokenPurchaseFixtureBuilder orderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public TokenPurchaseFixtureBuilder totalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public TokenPurchaseFixtureBuilder orderName(String orderName) {
        this.orderName = orderName;
        return this;
    }

    public TokenPurchaseFixtureBuilder productName(String productName) {
        this.productName = productName;
        return this;
    }

    public TokenPurchaseFixtureBuilder purchaseCount(Integer purchaseCount) {
        this.purchaseCount = purchaseCount;
        return this;
    }

    public TokenPurchaseFixtureBuilder count(Integer count) {
        this.purchaseCount = count;
        return this;
    }

    public TokenPurchaseFixtureBuilder unitPrice(Long unitPrice) {
        this.unitPrice = unitPrice;
        return this;
    }

    public TokenPurchaseFixtureBuilder state(TokenPurchaseState state) {
        this.state = state;
        return this;
    }

    public TokenPurchaseFixtureBuilder remainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
        return this;
    }

    public TokenPurchase build() {
        return new TokenPurchase(
                memberId != null ? memberId : 1L,
                paymentKey != null ? paymentKey : "test-payment-key-" + System.currentTimeMillis(),
                orderId != null ? orderId : "test-order-id-" + System.currentTimeMillis(),
                totalAmount != null ? totalAmount : 1000L,
                orderName != null ? orderName : "테스트 주문",
                productName != null ? productName : "token",
                purchaseCount != null ? purchaseCount : 10,
                unitPrice != null ? unitPrice : 100L,
                remainingCount != null ? remainingCount : (purchaseCount != null ? purchaseCount : 10),
                state != null ? state : TokenPurchaseState.REFUNDABLE
        );
    }
}
