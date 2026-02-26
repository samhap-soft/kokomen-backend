package com.samhap.kokomen.payment.service.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.samhap.kokomen.global.infrastructure.ObjectToStringDeserializer;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TossDateTimeDeserializer;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentResponse(
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
        List<TosspaymentsCancel> cancels
) {

    public static PaymentResponse from(TosspaymentsPaymentResponse response) {
        return new PaymentResponse(
                response.paymentKey(),
                response.type(),
                response.orderId(),
                response.orderName(),
                response.mId(),
                response.currency(),
                response.method(),
                response.totalAmount(),
                response.balanceAmount(),
                response.status(),
                response.requestedAt(),
                response.approvedAt(),
                response.lastTransactionKey(),
                response.suppliedAmount(),
                response.vat(),
                response.taxFreeAmount(),
                response.taxExemptionAmount(),
                response.isPartialCancelable(),
                response.metadata(),
                response.receipt() != null ? Receipt.from(response.receipt()) : null,
                response.checkout() != null ? Checkout.from(response.checkout()) : null,
                response.easyPay() != null ? EasyPay.from(response.easyPay()) : null,
                response.country(),
                response.failure() != null ? Failure.from(response.failure()) : null,
                response.cancels() != null ? response.cancels().stream()
                        .map(TosspaymentsCancel::from)
                        .toList() : null
        );
    }
}
