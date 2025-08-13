package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.InterviewMode;

public record AnswerRequest(
        String answer,
        InterviewMode mode
) {
}
