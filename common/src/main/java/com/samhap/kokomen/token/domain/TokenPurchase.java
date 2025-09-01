package com.samhap.kokomen.token.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

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
    private Integer remainingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenPurchaseState state;

    @Column(nullable = false)
    private Long unitPrice;

    @Enumerated(EnumType.STRING)
    @Column
    private RefundReasonCode refundReasonCode;

    @Column
    private String refundReasonText;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
        this.remainingCount = count;
        this.state = TokenPurchaseState.REFUNDABLE;
        this.unitPrice = unitPrice;
    }

    public TokenPurchase(Long memberId, String paymentKey, String orderId, Long totalAmount,
                         String orderName, String productName, Integer count, Long unitPrice,
                         Integer remainingCount, TokenPurchaseState state) {
        this.memberId = memberId;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.orderName = orderName;
        this.productName = productName;
        this.count = count;
        this.remainingCount = remainingCount;
        this.state = state;
        this.unitPrice = unitPrice;
    }

    public void useToken() {
        if (!hasRemainingTokens()) {
            throw new IllegalStateException("사용할 수 있는 토큰이 없습니다.");
        }

        this.remainingCount--;

        if (this.remainingCount == 0) {
            this.state = TokenPurchaseState.EXHAUSTED;
        } else if (this.state == TokenPurchaseState.REFUNDABLE) {
            this.state = TokenPurchaseState.USABLE;
        }
    }

    public boolean hasRemainingTokens() {
        return remainingCount > 0;
    }

    public void refund(RefundReasonCode refundReasonCode, String refundReasonText) {
        if (!isRefundable()) {
            throw new IllegalStateException("환불 불가능한 상태입니다.");
        }
        
        if (refundReasonCode == null) {
            throw new IllegalArgumentException("환불 사유 코드는 필수입니다.");
        }
        
        if (refundReasonCode.requiresReasonText() && (refundReasonText == null || refundReasonText.trim().isEmpty())) {
            throw new IllegalArgumentException("기타 환불 사유일 때는 상세한 사유를 입력해야 합니다.");
        }
        
        this.state = TokenPurchaseState.REFUNDED;
        this.remainingCount = 0;
        this.refundReasonCode = refundReasonCode;
        this.refundReasonText = refundReasonText;
    }

    public boolean isNotRefundable() {
        return !isRefundable();
    }

    public boolean isRefundable() {
        return state == TokenPurchaseState.REFUNDABLE && count.equals(remainingCount);
    }

    public boolean isNotOwnedBy(Long memberId) {
        return !this.memberId.equals(memberId);
    }
}
