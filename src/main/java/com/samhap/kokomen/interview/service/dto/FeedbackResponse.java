package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;

public record FeedbackResponse(
        Long questionId,
        Long answerId,
        String question,
        String answer,
        AnswerRank answerRank,
        String answerFeedback
) {

    public static FeedbackResponse from(Answer answer) {
        return new FeedbackResponse(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback()
        );
    }
}
