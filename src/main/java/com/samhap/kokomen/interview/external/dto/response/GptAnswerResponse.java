package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GptAnswerResponse(
        String rank,
        String feedback,
        @JsonProperty("next_question")
        String nextQuestion
) {
}
