package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;

public record GeneratedQuestionsResponse(
        Long id,
        String question
) {
    public static GeneratedQuestionsResponse from(GeneratedQuestion generatedQuestion) {
        return new GeneratedQuestionsResponse(
                generatedQuestion.getId(),
                generatedQuestion.getContent()
        );
    }
}
