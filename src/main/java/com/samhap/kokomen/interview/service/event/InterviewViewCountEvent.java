package com.samhap.kokomen.interview.service.event;

public record InterviewViewCountEvent(
        Long interviewId,
        Long receiverMemberId,
        Long viewCount
) {
}
