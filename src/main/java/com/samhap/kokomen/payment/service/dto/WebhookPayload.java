package com.samhap.kokomen.payment.service.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record WebhookPayload(
        String eventType,
        String createdAt,
        WebhookPaymentData data
) {
}
