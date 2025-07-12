package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewProceedResponse(
        AnswerRank curAnswerRank,
        Long nextQuestionId,
        String nextQuestion
) {

    public static InterviewProceedResponse createFollowingQuestionResponse(Answer curAnswer, Question nextQuestion) {
        return new InterviewProceedResponse(
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestion.getContent()
        );
    }
}
