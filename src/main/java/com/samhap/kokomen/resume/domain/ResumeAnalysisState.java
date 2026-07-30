package com.samhap.kokomen.resume.domain;

public enum ResumeAnalysisState {

    PENDING,
    EVALUATION_COMPLETED,
    COMPLETED,
    EVALUATION_FAILED,
    QUESTION_FAILED,
    ;

    public boolean isEvaluationRevealed() {
        return this == EVALUATION_COMPLETED || this == COMPLETED || this == QUESTION_FAILED;
    }

    public boolean isQuestionReady() {
        return this == COMPLETED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == EVALUATION_FAILED || this == QUESTION_FAILED;
    }
}
