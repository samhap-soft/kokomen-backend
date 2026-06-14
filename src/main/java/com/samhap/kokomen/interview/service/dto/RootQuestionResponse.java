package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionType;

public record RootQuestionResponse(
        Long id,
        RootQuestionType questionType,
        String title,
        String content
) {

    public static RootQuestionResponse from(RootQuestion rootQuestion) {
        return new RootQuestionResponse(
                rootQuestion.getId(),
                rootQuestion.getQuestionType(),
                rootQuestion.getTitle(),
                rootQuestion.getContent()
        );
    }
}
