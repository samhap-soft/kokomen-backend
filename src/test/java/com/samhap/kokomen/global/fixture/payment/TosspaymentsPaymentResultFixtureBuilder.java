package com.samhap.kokomen.global.fixture.payment;

import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import java.time.LocalDateTime;

public class TosspaymentsPaymentResultFixtureBuilder {

    private TosspaymentsPayment tosspaymentsPayment;
    private PaymentType type;
    private String mId;
    private String currency;
    private Long totalAmount;
    private String method;
    private Long balanceAmount;
    private TosspaymentsStatus tosspaymentsStatus;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private String lastTransactionKey;
    private Long suppliedAmount;
    private Long vat;
    private Long taxFreeAmount;
    private Long taxExemptionAmount;
    private boolean isPartialCancelable;
    private String receiptUrl;
    private String easyPayProvider;
    private Long easyPayAmount;
    private Long easyPayDiscountAmount;
    private String country;
    private String failureCode;
    private String failureMessage;

    public static TosspaymentsPaymentResultFixtureBuilder builder() {
        return new TosspaymentsPaymentResultFixtureBuilder();
    }

    public TosspaymentsPaymentResultFixtureBuilder tosspaymentsPayment(TosspaymentsPayment tosspaymentsPayment) {
        this.tosspaymentsPayment = tosspaymentsPayment;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder type(PaymentType type) {
        this.type = type;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder method(String method) {
        this.method = method;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder tosspaymentsStatus(TosspaymentsStatus status) {
        this.tosspaymentsStatus = status;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder approvedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder receiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder failureCode(String failureCode) {
        this.failureCode = failureCode;
        return this;
    }

    public TosspaymentsPaymentResultFixtureBuilder failureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
        return this;
    }

    public TosspaymentsPaymentResult build() {
        return new TosspaymentsPaymentResult(
                tosspaymentsPayment,
                type != null ? type : PaymentType.NORMAL,
                mId != null ? mId : "tvivarepublica",
                currency != null ? currency : "KRW",
                totalAmount != null ? totalAmount : 10000L,
                method != null ? method : "카드",
                balanceAmount != null ? balanceAmount : 10000L,
                tosspaymentsStatus != null ? tosspaymentsStatus : TosspaymentsStatus.DONE,
                requestedAt != null ? requestedAt : LocalDateTime.now().minusMinutes(5),
                approvedAt,
                lastTransactionKey != null ? lastTransactionKey : "test_transaction_key",
                suppliedAmount != null ? suppliedAmount : 9091L,
                vat != null ? vat : 909L,
                taxFreeAmount != null ? taxFreeAmount : 0L,
                taxExemptionAmount != null ? taxExemptionAmount : 0L,
                isPartialCancelable,
                receiptUrl,
                easyPayProvider,
                easyPayAmount,
                easyPayDiscountAmount,
                country != null ? country : "KR",
                failureCode,
                failureMessage
        );
    }
}
