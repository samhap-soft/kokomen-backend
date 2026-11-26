package com.samhap.kokomen.payment.service.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record WebhookPayload(
        String eventType,
        String createdAt,
        WebhookPaymentData data
) {
}
