package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.dto.AnswerMemos;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record FeedbackResponse(
        Long questionId,
        Long answerId,
        String question,
        String answer,
        AnswerRank answerRank,
        String answerFeedback,
        Long answerLikeCount,
        Boolean answerAlreadyLiked,
        String submittedAnswerMemoContent,
        String tempAnswerMemoContent,
        AnswerMemoVisibility answerMemoVisibility
) {
    public FeedbackResponse(Answer answer, Boolean answerAlreadyLiked, AnswerMemos answerMemos) {
        this(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback(),
                answer.getLikeCount(),
                answerAlreadyLiked,
                answerMemos.submittedAnswerMemo(),
                answerMemos.tempAnswerMemo(),
                answerMemos.answerMemoVisibility()
        );
    }

    public static List<FeedbackResponse> createMine(List<Answer> answers, Map<Long, AnswerMemos> answerMemos) {
        return answers.stream()
                .map(answer -> createMine(answer, answerMemos.get(answer.getId())))
                .sorted(Comparator.comparing(FeedbackResponse::questionId))
                .toList();
    }

    private static FeedbackResponse createMine(Answer answer, AnswerMemos answerMemos) {
        return new FeedbackResponse(
                answer.getQuestion().getId(),
                answer.getId(),
                answer.getQuestion().getContent(),
                answer.getContent(),
                answer.getAnswerRank(),
                answer.getFeedback(),
                null,
                null,
                answerMemos.submittedAnswerMemo(),
                answerMemos.tempAnswerMemo(),
                answerMemos.answerMemoVisibility()
        );
    }
}
