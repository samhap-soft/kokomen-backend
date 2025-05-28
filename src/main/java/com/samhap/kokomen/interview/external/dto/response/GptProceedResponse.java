package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GptProceedResponse(
        String rank,
        String feedback,
        @JsonProperty("next_question")
        String nextQuestion
) {
}
