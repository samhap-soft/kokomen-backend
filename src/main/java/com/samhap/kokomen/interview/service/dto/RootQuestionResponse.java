package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.RootQuestion;

public record RootQuestionResponse(
        Long id,
        String content
) {

    public static RootQuestionResponse from(RootQuestion rootQuestion) {
        return new RootQuestionResponse(
                rootQuestion.getId(),
                rootQuestion.getContent()
        );
    }
}
