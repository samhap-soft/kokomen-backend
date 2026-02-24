package com.samhap.kokomen.interview.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.interview.entity.InterviewMode;
import jakarta.validation.constraints.NotNull;

public record RootQuestionCustomInterviewRequest(
        @NotNull
        @JsonProperty("rootQuestionId")
        Long rootQuestionId,

        @NotNull
        @JsonProperty("maxQuestionCount")
        Integer maxQuestionCount,

        @NotNull
        InterviewMode mode
) {
}
