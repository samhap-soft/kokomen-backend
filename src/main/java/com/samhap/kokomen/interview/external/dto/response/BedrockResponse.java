package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;

public record BedrockResponse(
        String content
) {

    // TODO: GPT와 Bedrock이 통합적으로 사용할 수 있는 이름으로 변경. 예를 들어 InterviewXXX
    public GptFeedbackResponse extractGptFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, GptFeedbackResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패", e);
        }
    }

    public GptNextQuestionResponse extractGptNextQuestionResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, GptNextQuestionResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패", e);
        }
    }

    public GptTotalFeedbackResponse extractGptTotalFeedbackResponse(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(content, GptTotalFeedbackResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Bedrock 응답 파싱 실패", e);
        }
    }
} 
