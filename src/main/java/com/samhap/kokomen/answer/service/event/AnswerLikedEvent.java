package com.samhap.kokomen.answer.service.event;

public record AnswerLikedEvent(
        Long answerId,
        Long interviewId,
        Long likerMemberId,
        Long receiverMemberId,
        Long likeCount
) {
}
