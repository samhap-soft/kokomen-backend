package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.global.domain.NotificationType;

// TODO: Kafka를 제대로 적용하게 되는 시점에 삭제
public record InterviewLikeNotificationPayload(
        NotificationType notificationType,
        Long interviewId,
        Long likerMemberId,
        Long likeCount
) implements NotificationPayload {
}
