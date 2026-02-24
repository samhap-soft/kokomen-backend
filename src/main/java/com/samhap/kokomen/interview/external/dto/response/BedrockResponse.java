package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.samhap.kokomen.global.exception.ExternalApiException;

public record BedrockResponse(
        String content
) implements LlmResponse {

    @Override
    public AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper) {
        return parseContent(objectMapper, AnswerFeedbackResponse.class);
    }

    @Override
    public AnswerRankResponse extractAnswerRankResponse(ObjectMapper objectMapper) {
        return parseContent(objectMapper, AnswerRankResponse.class);
    }

    @Override
    public NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper) {
        return parseContent(objectMapper, NextQuestionResponse.class);
    }

    @Override
    public TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper) {
        return parseContent(objectMapper, TotalFeedbackResponse.class);
    }

    private <T> T parseContent(ObjectMapper objectMapper, Class<T> type) {
        try {
            ObjectReader reader = objectMapper.reader()
                    .with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
            return reader.readValue(content, type);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock 응답 파싱 실패. 원본 응답: " + content, e);
        }
    }
}
