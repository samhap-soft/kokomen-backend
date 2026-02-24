package com.samhap.kokomen.global.fixture.payment;

import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;

public class TosspaymentsPaymentFixtureBuilder {

    private String paymentKey;
    private Long memberId;
    private String orderId;
    private String orderName;
    private Long totalAmount;
    private String metadata;
    private ServiceType serviceType;

    public static TosspaymentsPaymentFixtureBuilder builder() {
        return new TosspaymentsPaymentFixtureBuilder();
    }

    public TosspaymentsPaymentFixtureBuilder paymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder memberId(Long memberId) {
        this.memberId = memberId;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder orderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder orderName(String orderName) {
        this.orderName = orderName;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder totalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder metadata(String metadata) {
        this.metadata = metadata;
        return this;
    }

    public TosspaymentsPaymentFixtureBuilder serviceType(ServiceType serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public TosspaymentsPayment build() {
        return new TosspaymentsPayment(
                paymentKey != null ? paymentKey : "test_payment_key_123",
                memberId != null ? memberId : 1L,
                orderId != null ? orderId : "order_123",
                orderName != null ? orderName : "테스트 주문",
                totalAmount != null ? totalAmount : 10000L,
                metadata != null ? metadata : "{\"test\": \"metadata\"}",
                serviceType != null ? serviceType : ServiceType.INTERVIEW
        );
    }
}
