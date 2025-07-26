package com.samhap.kokomen.answer.service.dto;

import com.samhap.kokomen.global.domain.NotificationType;
import com.samhap.kokomen.interview.service.dto.NotificationPayload;

public record AnswerLikeNotificationPayload(
        NotificationType notificationType,
        Long answerId,
        Long interviewId,
        Long likerMemberId,
        Long likeCount
) implements NotificationPayload {
}
