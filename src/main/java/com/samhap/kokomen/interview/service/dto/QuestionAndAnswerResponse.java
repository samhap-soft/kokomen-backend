package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;

public record QuestionAndAnswerResponse(
        Long questionId,
        String question,
        Long answerId,
        String answer
) {

    public QuestionAndAnswerResponse(Answer answer) {
        this(answer.getQuestion().getId(), answer.getQuestion().getContent(), answer.getId(), answer.getContent());
    }
}
