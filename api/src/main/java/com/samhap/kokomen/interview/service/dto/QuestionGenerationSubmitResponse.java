package com.samhap.kokomen.interview.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuestionGenerationSubmitResponse(
        @JsonProperty("interview_id")
        Long interviewId
) {
}
