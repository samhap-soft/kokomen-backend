package com.samhap.kokomen.global.fixture.payment;

import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.EasyPay;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import java.time.LocalDateTime;
import java.util.List;

// 토스 Payment 객체는 type, mId, currency, balanceAmount, requestedAt을 항상 채워 보낸다.
// 결과 행(tosspayments_payment_result)의 NOT NULL 컬럼이 이 값들이므로 기본값으로 채워 둔다.
public class TosspaymentsPaymentResponseFixtureBuilder {

    private String paymentKey;
    private String orderId;
    private String method;
    private Long totalAmount;
    private Long balanceAmount;
    private TosspaymentsStatus status;
    private EasyPay easyPay;
    private List<TosspaymentsCancel> cancels;

    public static TosspaymentsPaymentResponseFixtureBuilder builder() {
        return new TosspaymentsPaymentResponseFixtureBuilder();
    }

    public TosspaymentsPaymentResponseFixtureBuilder paymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder orderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder method(String method) {
        this.method = method;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder totalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder balanceAmount(Long balanceAmount) {
        this.balanceAmount = balanceAmount;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder status(TosspaymentsStatus status) {
        this.status = status;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder easyPay(EasyPay easyPay) {
        this.easyPay = easyPay;
        return this;
    }

    public TosspaymentsPaymentResponseFixtureBuilder cancels(List<TosspaymentsCancel> cancels) {
        this.cancels = cancels;
        return this;
    }

    public TosspaymentsPaymentResponse build() {
        Long resolvedTotalAmount = totalAmount != null ? totalAmount : 10000L;
        return new TosspaymentsPaymentResponse(
                paymentKey != null ? paymentKey : "test_payment_key_123",
                PaymentType.NORMAL,
                orderId != null ? orderId : "order_123",
                "테스트 주문",
                "tvivarepublica",
                "KRW",
                method != null ? method : "카드",
                resolvedTotalAmount,
                balanceAmount != null ? balanceAmount : resolvedTotalAmount,
                status != null ? status : TosspaymentsStatus.DONE,
                LocalDateTime.of(2024, 1, 1, 12, 0),
                LocalDateTime.of(2024, 1, 1, 12, 1),
                "test_transaction_key",
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                easyPay,
                "KR",
                null,
                cancels
        );
    }
}
