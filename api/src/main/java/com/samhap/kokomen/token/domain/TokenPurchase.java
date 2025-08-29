package com.samhap.kokomen.token.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "token_purchase", indexes = {
        @Index(name = "idx_token_purchase_payment_key", columnList = "payment_key")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private String orderName;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer count;

    @Column(nullable = false)
    private Long unitPrice;

    @Builder
    public TokenPurchase(Long memberId, String paymentKey, String orderId, Long totalAmount,
                         String orderName, String productName, Integer count, Long unitPrice) {
        this.memberId = memberId;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.orderName = orderName;
        this.productName = productName;
        this.count = count;
        this.unitPrice = unitPrice;
    }
}
