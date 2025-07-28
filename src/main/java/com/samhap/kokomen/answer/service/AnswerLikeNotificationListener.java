package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.service.dto.AnswerLikeNotificationPayload;
import com.samhap.kokomen.answer.service.event.AnswerLikedEvent;
import com.samhap.kokomen.global.domain.NotificationType;
import com.samhap.kokomen.interview.external.NotificationClient;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class AnswerLikeNotificationListener {

    private final NotificationClient notificationClient;

    @Async("asyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendLikeNotificationAsync(AnswerLikedEvent event) {
        AnswerLikeNotificationPayload notificationPayload = new AnswerLikeNotificationPayload(
                NotificationType.ANSWER_LIKE, event.answerId(), event.interviewId(), event.likerMemberId(), event.likeCount());
        NotificationRequest notificationRequest = new NotificationRequest(event.receiverMemberId(), notificationPayload);

        notificationClient.request(notificationRequest);
    }
} 
