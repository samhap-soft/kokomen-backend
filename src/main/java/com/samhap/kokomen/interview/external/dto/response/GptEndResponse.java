package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GptEndResponse(
        String rank,
        String feedback,
        @JsonProperty("total_feedback")
        String totalFeedback
) {
}
