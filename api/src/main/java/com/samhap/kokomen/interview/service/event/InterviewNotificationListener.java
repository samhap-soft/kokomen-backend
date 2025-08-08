package com.samhap.kokomen.interview.service.event;

import com.samhap.kokomen.global.domain.NotificationType;
import com.samhap.kokomen.interview.external.NotificationClient;
import com.samhap.kokomen.interview.service.dto.InterviewLikeNotificationPayload;
import com.samhap.kokomen.interview.service.dto.InterviewViewCountNotificationPayload;
import com.samhap.kokomen.interview.service.dto.NotificationRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class InterviewNotificationListener {

    private static final Set<Long> VIEW_COUNT_NOTIFICATION_CONDITIONS;

    static {
        Set<Long> viewCountSet = new HashSet<>();
        Long viewCount = 1L;
        while (viewCount <= Long.MAX_VALUE / 10) {
            viewCountSet.add(viewCount);
            viewCount *= 10;
        }
        viewCountSet.add(viewCount);

        VIEW_COUNT_NOTIFICATION_CONDITIONS = Collections.unmodifiableSet(viewCountSet);
    }

    private final NotificationClient notificationClient;

    @Async
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

    @Async
    @EventListener(condition = "T(com.samhap.kokomen.interview.service.event.InterviewNotificationListener).isOneOrPowerOfTen(#viewCountEvent.viewCount())")
    public void sendViewCountNotificationAsync(InterviewViewCountEvent viewCountEvent) {
        InterviewViewCountNotificationPayload notificationPayload = new InterviewViewCountNotificationPayload(
                NotificationType.INTERVIEW_VIEW_COUNT, viewCountEvent.interviewId(), viewCountEvent.viewCount());
        NotificationRequest notificationRequest = new NotificationRequest(viewCountEvent.receiverMemberId(), notificationPayload);

        notificationClient.request(notificationRequest);
    }

    public static boolean isOneOrPowerOfTen(long viewCount) {
        return VIEW_COUNT_NOTIFICATION_CONDITIONS.contains(viewCount);
    }
} 
