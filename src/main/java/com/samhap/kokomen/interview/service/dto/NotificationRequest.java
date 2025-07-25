package com.samhap.kokomen.interview.service.dto;

public record NotificationRequest(
        Long receiverMemberId,
        NotificationPayload notificationPayload
) {
}
