package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;

public record BedrockResponse(
        String content
) implements LlmResponse {

    @Override
    public AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, AnswerFeedbackResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }

    @Override
    public NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, NextQuestionResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }

    @Override
    public TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, TotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }
}
