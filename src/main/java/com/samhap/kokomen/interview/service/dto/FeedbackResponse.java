package com.samhap.kokomen.interview.service.dto;

import java.util.Comparator;
import java.util.List;

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
    public FeedbackResponse(Answer answer) {
        this(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback()
        );
    }

    public static List<FeedbackResponse> from(List<Answer> answers) {
        return answers.stream()
                .map(FeedbackResponse::new)
                .sorted(Comparator.comparing(FeedbackResponse::questionId))
                .toList();
    }
}
