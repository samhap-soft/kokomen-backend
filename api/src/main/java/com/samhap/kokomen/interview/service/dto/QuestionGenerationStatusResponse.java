package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;

public record QuestionGenerationStatusResponse(
        ResumeQuestionGenerationState status
) {
    public static QuestionGenerationStatusResponse of(ResumeQuestionGenerationState status) {
        return new QuestionGenerationStatusResponse(status);
    }
}
