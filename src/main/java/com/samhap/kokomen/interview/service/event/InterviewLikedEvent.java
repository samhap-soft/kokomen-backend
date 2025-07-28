package com.samhap.kokomen.interview.service.event;

public record InterviewLikedEvent(
        Long interviewId,
        Long likerMemberId,
        Long receiverMemberId,
        Long likeCount
) {
}
