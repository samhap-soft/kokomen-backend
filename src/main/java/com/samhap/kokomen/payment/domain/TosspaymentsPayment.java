package com.samhap.kokomen.payment.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.LastModifiedDate;

@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tosspayments_payment", indexes = {
        @Index(name = "idx_payment_member_id", columnList = "member_id")
})
public class TosspaymentsPayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_key", nullable = false, unique = true)
    private String paymentKey;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "metadata", columnDefinition = "json", nullable = false)
    private String metadata;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentState state;

    @Column(name = "service_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public TosspaymentsPayment(String paymentKey, Long memberId, String orderId, String orderName, Long totalAmount,
                               String metadata, ServiceType serviceType) {
        validateConstructorParams(paymentKey, memberId, orderId, totalAmount);
        this.paymentKey = paymentKey;
        this.memberId = memberId;
        this.orderId = orderId;
        this.orderName = orderName;
        this.totalAmount = totalAmount;
        this.metadata = metadata;
        this.serviceType = serviceType;
        this.state = PaymentState.NEED_APPROVE;
    }

    private void validateConstructorParams(String paymentKey, Long memberId, String orderId, Long totalAmount) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId는 필수입니다.");
        }
        if (totalAmount == null || totalAmount < 0) {
            throw new IllegalArgumentException("totalAmount는 0 이상이어야 합니다.");
        }
    }

    public void updateState(PaymentState state) {
        this.state = state;
    }

    public void validateTosspaymentsResult(String paymentKey, String orderId, Long totalAmount) {
        if (!this.paymentKey.equals(paymentKey)) {
            log.error("paymentKey 불일치 - 응답: {}, DB: {}", maskPaymentKey(paymentKey), maskPaymentKey(this.paymentKey));
            throw new InternalServerErrorException(PaymentErrorMessage.PAYMENT_KEY_MISMATCH.getMessage());
        }
        if (!this.orderId.equals(orderId)) {
            log.error("orderId 불일치 - 응답: {}, DB: {}", orderId, this.orderId);
            throw new InternalServerErrorException(PaymentErrorMessage.ORDER_ID_MISMATCH.getMessage());
        }
        if (!this.totalAmount.equals(totalAmount)) {
            log.error("totalAmount 불일치 - 응답: {}, DB: {}", totalAmount, this.totalAmount);
            throw new InternalServerErrorException(PaymentErrorMessage.TOTAL_AMOUNT_MISMATCH.getMessage());
        }
    }

    private String maskPaymentKey(String key) {
        if (key == null || key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 8) + "***";
    }
}
