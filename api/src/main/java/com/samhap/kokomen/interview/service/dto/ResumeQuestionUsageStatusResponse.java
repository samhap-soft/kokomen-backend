package com.samhap.kokomen.interview.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResumeQuestionUsageStatusResponse(
        @JsonProperty("is_first_use")
        boolean isFirstUse
) {
    public static ResumeQuestionUsageStatusResponse of(boolean isFirstUse) {
        return new ResumeQuestionUsageStatusResponse(isFirstUse);
    }
}
