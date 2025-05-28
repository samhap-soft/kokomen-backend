package com.samhap.kokomen.interview.service.dto;

public record InterviewResponse(
        Long interviewId,
        Long questionId,
        String rootQuestion
) {
}
