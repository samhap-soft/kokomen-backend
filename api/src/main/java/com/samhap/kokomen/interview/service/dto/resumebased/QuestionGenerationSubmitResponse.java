package com.samhap.kokomen.interview.service.dto.resumebased;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuestionGenerationSubmitResponse(
        @JsonProperty("resume_based_interview_result_id")
        Long resumeBasedInterviewResultId
) {
}
