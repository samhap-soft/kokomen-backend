package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.DocumentJsonConverter;
import software.amazon.awssdk.core.document.Document;

public record BedrockConverseResponse(
        Document toolInput
) implements LlmResponse {

    @Override
    public AnswerFeedbackResponse extractAnswerFeedbackResponse(ObjectMapper objectMapper) {
        return mapTo(toolInput, AnswerFeedbackResponse.class, objectMapper);
    }

    @Override
    public AnswerRankResponse extractAnswerRankResponse(ObjectMapper objectMapper) {
        return mapTo(toolInput, AnswerRankResponse.class, objectMapper);
    }

    @Override
    public NextQuestionResponse extractNextQuestionResponse(ObjectMapper objectMapper) {
        return mapTo(toolInput, NextQuestionResponse.class, objectMapper);
    }

    @Override
    public TotalFeedbackResponse extractTotalFeedbackResponse(ObjectMapper objectMapper) {
        return mapTo(toolInput, TotalFeedbackResponse.class, objectMapper);
    }

    private static <T> T mapTo(Document document, Class<T> type, ObjectMapper objectMapper) {
        try {
            Object javaObject = DocumentJsonConverter.toJavaObject(document);
            return objectMapper.convertValue(javaObject, type);
        } catch (Exception e) {
            throw new ExternalApiException("Bedrock toolUse 파싱 실패: type=" + type.getSimpleName(), e);
        }
    }
}
