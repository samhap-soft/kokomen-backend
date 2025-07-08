package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerRank;
import java.util.Comparator;
import java.util.List;

public record FeedbackResponse(
        Long questionId,
        Long answerId,
        String question,
        String answer,
        AnswerRank answerRank,
        String answerFeedback,
        Integer answerLikeCount
) {
    public FeedbackResponse(Answer answer) {
        this(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback(),
                answer.getLikeCount()
        );
    }

    public static List<FeedbackResponse> from(List<Answer> answers) {
        return answers.stream()
                .map(FeedbackResponse::new)
                .sorted(Comparator.comparing(FeedbackResponse::questionId))
                .toList();
    }

    public static List<FeedbackResponse> createMine(List<Answer> answers) {
        return answers.stream()
                .map(FeedbackResponse::createMine)
                .sorted(Comparator.comparing(FeedbackResponse::questionId))
                .toList();
    }

    private static FeedbackResponse createMine(Answer answer) {
        return new FeedbackResponse(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback(),
                null
        );
    }
}
