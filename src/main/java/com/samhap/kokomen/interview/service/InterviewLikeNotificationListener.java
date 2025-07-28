package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.domain.NotificationType;
import com.samhap.kokomen.interview.external.NotificationClient;
import com.samhap.kokomen.interview.service.dto.InterviewLikeNotificationPayload;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import com.samhap.kokomen.interview.service.event.InterviewLikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class InterviewLikeNotificationListener {

    private final NotificationClient notificationClient;

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendLikeNotificationAsync(InterviewLikedEvent event) {
        InterviewLikeNotificationPayload notificationPayload = new InterviewLikeNotificationPayload(
                NotificationType.INTERVIEW_LIKE, event.interviewId(), event.likerMemberId(), event.likeCount());
        NotificationRequest notificationRequest = new NotificationRequest(event.receiverMemberId(), notificationPayload);

        notificationClient.request(notificationRequest);
    }
} 
