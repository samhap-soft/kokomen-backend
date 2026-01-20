package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;

public record QuestionGenerationStateResponse(
        ResumeQuestionGenerationState state
) {
    public static QuestionGenerationStateResponse of(ResumeQuestionGenerationState state) {
        return new QuestionGenerationStateResponse(state);
    }
}
