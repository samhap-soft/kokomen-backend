package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.AnswerRank;

public record RootQuestionReferenceAnswer(
        String nickname,
        Long interviewId,
        String answerContent,
        AnswerRank answerRank
) {
}