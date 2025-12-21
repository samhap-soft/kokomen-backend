package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.domain.ResumeEvaluationState;
import java.time.LocalDateTime;

public record ResumeEvaluationHistoryResponse(
        Long id,
        ResumeEvaluationState state,
        String jobPosition,
        String jobCareer,
        Integer totalScore,
        LocalDateTime createdAt
) {
    public static ResumeEvaluationHistoryResponse from(ResumeEvaluation evaluation) {
        return new ResumeEvaluationHistoryResponse(
                evaluation.getId(),
                evaluation.getState(),
                evaluation.getJobPosition(),
                evaluation.getJobCareer(),
                evaluation.getTotalScore(),
                evaluation.getCreatedAt()
        );
    }
}
