package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;

public record GeneratedQuestionResponse(
        int index,
        String question,
        String reason
) {
    public static GeneratedQuestionResponse from(int index, GeneratedQuestionDto dto) {
        return new GeneratedQuestionResponse(index, dto.question(), dto.reason());
    }
}
