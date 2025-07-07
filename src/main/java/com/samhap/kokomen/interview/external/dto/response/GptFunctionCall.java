package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.LlmApiException;

public record GptFunctionCall(
        String name,
        String arguments
) {

    public AnswerFeedbackResponse extractGptFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, AnswerFeedbackResponse.class);
        } catch (Exception e) {
            throw new LlmApiException("GPT 응답 파싱 실패", e);
        }
    }

    public NextQuestionResponse extractGptNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, NextQuestionResponse.class);
        } catch (Exception e) {
            throw new LlmApiException("GPT 응답 파싱 실패", e);
        }
    }

    public TotalFeedbackResponse extractGptTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(arguments, TotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new LlmApiException("GPT 응답 파싱 실패", e);
        }
    }
}
