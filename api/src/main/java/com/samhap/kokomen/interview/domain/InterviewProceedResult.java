package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.answer.domain.Answer;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InterviewProceedResult {

    private final Answer curAnswer;
    private final Question nextQuestion;

    public static InterviewProceedResult createInProgress(Answer curAnswer, Question nextQuestion) {
        return new InterviewProceedResult(curAnswer, nextQuestion);
    }

    public static InterviewProceedResult createFinished(Answer curAnswer) {
        return new InterviewProceedResult(curAnswer, null);
    }

    public boolean isInProgress() {
        return nextQuestion != null;
    }

    public Answer getCurAnswer() {
        return curAnswer;
    }

    public Question getNextQuestion() {
        Objects.requireNonNull(nextQuestion, "완료된 인터뷰는 다음 질문이 없습니다.");
        return nextQuestion;
    }
}
