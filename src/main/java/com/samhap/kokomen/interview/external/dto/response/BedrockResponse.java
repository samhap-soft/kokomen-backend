package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public record BedrockResponse(
        String content
) {

    public AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, AnswerFeedbackResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }

    public NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, NextQuestionResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }

    public TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, TotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }
}
