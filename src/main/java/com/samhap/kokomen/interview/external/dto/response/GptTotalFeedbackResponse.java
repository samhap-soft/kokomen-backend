package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GptTotalFeedbackResponse(
        @JsonProperty("total_feedback")
        String totalFeedback
) {
}
