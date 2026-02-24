package com.samhap.kokomen.payment.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class TosspaymentsPaymentResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tosspayments_payment_id", nullable = false)
    private TosspaymentsPayment tosspaymentsPayment;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentType type;

    @Column(name = "m_id", nullable = false)
    private String mId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "method")
    private String method;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "balance_amount", nullable = false)
    private Long balanceAmount;

    @Column(name = "tosspayments_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TosspaymentsStatus tosspaymentsStatus;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "last_transaction_key")
    private String lastTransactionKey;

    @Column(name = "supplied_amount")
    private Long suppliedAmount;

    @Column(name = "vat")
    private Long vat;

    @Column(name = "tax_free_amount")
    private Long taxFreeAmount;

    @Column(name = "tax_exemption_amount")
    private Long taxExemptionAmount;

    @Column(name = "is_partial_cancelable", nullable = false)
    private boolean isPartialCancelable;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "easy_pay_provider")
    private String easyPayProvider;

    @Column(name = "easy_pay_amount")
    private Long easyPayAmount;

    @Column(name = "easy_pay_discount_amount")
    private Long easyPayDiscountAmount;

    @Column(name = "country")
    private String country;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "cancel_status")
    private String cancelStatus;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public TosspaymentsPaymentResult(TosspaymentsPayment tosspaymentsPayment, PaymentType type, String mId, String currency, Long totalAmount, String method,
                                     Long balanceAmount, TosspaymentsStatus tosspaymentsStatus, LocalDateTime requestedAt, LocalDateTime approvedAt,
                                     String lastTransactionKey, Long suppliedAmount, Long vat, Long taxFreeAmount, Long taxExemptionAmount,
                                     boolean isPartialCancelable, String receiptUrl, String easyPayProvider, Long easyPayAmount,
                                     Long easyPayDiscountAmount, String country, String failureCode, String failureMessage) {
        this.tosspaymentsPayment = tosspaymentsPayment;
        this.type = type;
        this.mId = mId;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.method = method;
        this.balanceAmount = balanceAmount;
        this.tosspaymentsStatus = tosspaymentsStatus;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.lastTransactionKey = lastTransactionKey;
        this.suppliedAmount = suppliedAmount;
        this.vat = vat;
        this.taxFreeAmount = taxFreeAmount;
        this.taxExemptionAmount = taxExemptionAmount;
        this.isPartialCancelable = isPartialCancelable;
        this.receiptUrl = receiptUrl;
        this.easyPayProvider = easyPayProvider;
        this.easyPayAmount = easyPayAmount;
        this.easyPayDiscountAmount = easyPayDiscountAmount;
        this.country = country;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void updateCancelInfo(String cancelReason, LocalDateTime canceledAt, Long easyPayDiscountAmount,
                                 String lastTransactionKey, String cancelStatus, TosspaymentsStatus tosspaymentsStatus) {
        this.cancelReason = cancelReason;
        this.canceledAt = canceledAt;
        this.easyPayDiscountAmount = easyPayDiscountAmount;
        this.lastTransactionKey = lastTransactionKey;
        this.cancelStatus = cancelStatus;
        this.tosspaymentsStatus = tosspaymentsStatus;
    }
}
