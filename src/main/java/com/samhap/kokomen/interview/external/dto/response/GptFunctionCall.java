package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.GptApiException;

public record GptFunctionCall(
        String name,
        String arguments
) {

    public GptFeedbackResponse extractGptFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptFeedbackResponse.class);
        } catch (Exception e) {
            throw new GptApiException("GPT 응답 파싱 실패", e);
        }
    }

    public GptNextQuestionResponse extractGptNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptNextQuestionResponse.class);
        } catch (Exception e) {
            throw new GptApiException("GPT 응답 파싱 실패", e);
        }
    }

    public GptTotalFeedbackResponse extractGptTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, GptTotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new GptApiException("GPT 응답 파싱 실패", e);
        }
    }
}
