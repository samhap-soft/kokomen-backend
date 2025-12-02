package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotNull;

public record RootQuestionCustomInterviewRequest(
        @NotNull
        Long rootQuestionId,

        @NotNull
        Integer maxQuestionCount,

        @NotNull
        InterviewMode mode
) {
}
