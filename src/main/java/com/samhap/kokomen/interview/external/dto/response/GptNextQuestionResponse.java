package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GptNextQuestionResponse(
        @JsonProperty("next_question")
        String nextQuestion
) {
}
