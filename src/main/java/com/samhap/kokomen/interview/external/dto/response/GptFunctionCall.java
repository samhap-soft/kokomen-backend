package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public record GptFunctionCall(
        String name,
        String arguments
) {

    public GptFeedbackResponse extractGptFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptFeedbackResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }

    public GptNextQuestionResponse extractGptNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptNextQuestionResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }

    public GptTotalFeedbackResponse extractGptTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptTotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("GPT 응답 파싱 실패", e);
        }
    }
}
