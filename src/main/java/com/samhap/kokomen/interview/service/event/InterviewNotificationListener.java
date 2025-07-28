package com.samhap.kokomen.interview.service.event;

import com.samhap.kokomen.global.domain.NotificationType;
import com.samhap.kokomen.interview.external.NotificationClient;
import com.samhap.kokomen.interview.service.dto.InterviewLikeNotificationPayload;
import com.samhap.kokomen.interview.service.dto.InterviewViewCountNotificationPayload;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class InterviewNotificationListener {

    private final NotificationClient notificationClient;

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendLikeNotificationAsync(InterviewLikedEvent likeEvent) {
        if (likeEvent.receiverMemberId().equals(likeEvent.likerMemberId())) {
            return;
        }

        InterviewLikeNotificationPayload notificationPayload = new InterviewLikeNotificationPayload(
                NotificationType.INTERVIEW_LIKE, likeEvent.interviewId(), likeEvent.likerMemberId(), likeEvent.likeCount());
        NotificationRequest notificationRequest = new NotificationRequest(likeEvent.receiverMemberId(), notificationPayload);

        notificationClient.request(notificationRequest);
    }

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendViewCountNotificationAsync(InterviewViewCountEvent viewCountEvent) {
        InterviewViewCountNotificationPayload notificationPayload = new InterviewViewCountNotificationPayload(
                NotificationType.INTERVIEW_VIEW_COUNT, viewCountEvent.interviewId(), viewCountEvent.viewCount());
        NotificationRequest notificationRequest = new NotificationRequest(viewCountEvent.receiverMemberId(), notificationPayload);

        notificationClient.request(notificationRequest);
    }
} 
