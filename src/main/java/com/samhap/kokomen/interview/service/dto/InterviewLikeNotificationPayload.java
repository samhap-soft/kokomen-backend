package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.global.domain.NotificationType;

public record InterviewLikeNotificationPayload(
        NotificationType notificationType,
        Long interviewId,
        Long likerMemberId,
        Long likeCount
) implements NotificationPayload {
}
