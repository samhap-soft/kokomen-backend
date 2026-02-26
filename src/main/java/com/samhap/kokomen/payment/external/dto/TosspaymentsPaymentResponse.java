package com.samhap.kokomen.payment.external.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.samhap.kokomen.global.infrastructure.ObjectToStringDeserializer;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import java.time.LocalDateTime;

public record TosspaymentsPaymentResponse(
        String paymentKey,
        PaymentType type,
        String orderId,
        String orderName,
        String mId,
        String currency,
        String method,
        Long totalAmount,
        Long balanceAmount,
        TosspaymentsStatus status,
        @JsonDeserialize(using = TossDateTimeDeserializer.class)
        LocalDateTime requestedAt,
        @JsonDeserialize(using = TossDateTimeDeserializer.class)
        LocalDateTime approvedAt,
        String lastTransactionKey,
        Long suppliedAmount,
        Long vat,
        Long taxFreeAmount,
        Long taxExemptionAmount,
        boolean isPartialCancelable,
        @JsonDeserialize(using = ObjectToStringDeserializer.class)
        String metadata,
        Receipt receipt,
        Checkout checkout,
        EasyPay easyPay,
        String country,
        Failure failure,
        java.util.List<TosspaymentsCancel> cancels
) {

    public TosspaymentsPaymentResult toTosspaymentsPaymentResult(TosspaymentsPayment tosspaymentsPayment) {
        return TosspaymentsPaymentResult.builder()
                .tosspaymentsPayment(tosspaymentsPayment)
                .type(this.type)
                .mId(this.mId)
                .currency(this.currency)
                .totalAmount(this.totalAmount)
                .method(this.method)
                .balanceAmount(this.balanceAmount)
                .tosspaymentsStatus(this.status)
                .requestedAt(this.requestedAt)
                .approvedAt(this.approvedAt)
                .lastTransactionKey(this.lastTransactionKey)
                .suppliedAmount(this.suppliedAmount)
                .vat(this.vat)
                .taxFreeAmount(this.taxFreeAmount)
                .taxExemptionAmount(this.taxExemptionAmount)
                .isPartialCancelable(this.isPartialCancelable)
                .receiptUrl(this.receipt() != null ? this.receipt().url() : null)
                .easyPayProvider(this.easyPay() != null ? this.easyPay().provider() : null)
                .easyPayAmount(this.easyPay() != null ? this.easyPay().amount() : null)
                .easyPayDiscountAmount(this.easyPay() != null ? this.easyPay().discountAmount() : null)
                .country(this.country)
                .failureCode(this.failure() != null ? this.failure().code() : null)
                .failureMessage(this.failure() != null ? this.failure().message() : null)
                .build();
    }
}
