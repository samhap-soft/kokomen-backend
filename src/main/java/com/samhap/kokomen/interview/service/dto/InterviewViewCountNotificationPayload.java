package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.global.domain.NotificationType;

public record InterviewViewCountNotificationPayload(
        NotificationType notificationType,
        Long interviewId,
        Long viewCount
) implements NotificationPayload {
}
