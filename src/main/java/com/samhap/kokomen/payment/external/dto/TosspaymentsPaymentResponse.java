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
        return new TosspaymentsPaymentResult(
                tosspaymentsPayment,
                this.type,
                this.mId,
                this.currency,
                this.totalAmount,
                this.method,
                this.balanceAmount,
                this.status,
                this.requestedAt,
                this.approvedAt,
                this.lastTransactionKey,
                this.suppliedAmount,
                this.vat,
                this.taxFreeAmount,
                this.taxExemptionAmount,
                this.isPartialCancelable,
                this.receipt() != null ? this.receipt().url() : null,
                this.easyPay() != null ? this.easyPay().provider() : null,
                this.easyPay() != null ? this.easyPay().amount() : null,
                this.easyPay() != null ? this.easyPay().discountAmount() : null,
                this.country,
                this.failure() != null ? this.failure().code() : null,
                this.failure() != null ? this.failure().message() : null
        );
    }
}
