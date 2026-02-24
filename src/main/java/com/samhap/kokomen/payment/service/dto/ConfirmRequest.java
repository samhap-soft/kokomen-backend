package com.samhap.kokomen.payment.service.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.samhap.kokomen.global.infrastructure.ObjectToStringDeserializer;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.external.dto.TosspaymentsConfirmRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmRequest(
        @NotBlank(message = "payment_key는 비어있거나 공백일 수 없습니다.")
        String paymentKey,
        @NotBlank(message = "order_id는 비어있거나 공백일 수 없습니다.")
        String orderId,
        @NotNull(message = "total_amount는 null일 수 없습니다.")
        Long totalAmount,
        @NotBlank(message = "order_name은 비어있거나 공백일 수 없습니다.")
        String orderName,
        @NotNull(message = "member_id는 null일 수 없습니다.")
        Long memberId,
        @JsonDeserialize(using = ObjectToStringDeserializer.class)
        @NotBlank(message = "metadata는 비어있거나 공백일 수 없습니다.")
        String metadata,
        @NotNull(message = "service_type은 null일 수 없습니다.")
        ServiceType serviceType
) {

    public TosspaymentsConfirmRequest toTosspaymentsConfirmRequest() {
        return new TosspaymentsConfirmRequest(paymentKey, orderId, totalAmount);
    }

    public TosspaymentsPayment toTosspaymentsPayment() {
        return new TosspaymentsPayment(paymentKey, memberId, orderId, orderName, totalAmount, metadata, serviceType);
    }
}
