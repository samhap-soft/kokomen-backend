package com.samhap.kokomen.interview.service.dto.resumebased;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotNull;

public record ResumeBasedInterviewStartRequest(
        @NotNull
        @JsonProperty("generated_question_id")
        Long generatedQuestionId,

        @NotNull
        @JsonProperty("max_question_count")
        Integer maxQuestionCount,

        @NotNull
        InterviewMode mode
) {
}
