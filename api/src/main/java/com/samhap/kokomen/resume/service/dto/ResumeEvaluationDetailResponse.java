package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.domain.ResumeEvaluationState;
import java.time.LocalDateTime;

public record ResumeEvaluationDetailResponse(
        Long id,
        ResumeEvaluationState state,
        String resume,
        String portfolio,
        String jobPosition,
        String jobDescription,
        String jobCareer,
        ResumeEvaluationResponse result,
        LocalDateTime createdAt
) {
    public static ResumeEvaluationDetailResponse from(ResumeEvaluation evaluation) {
        ResumeEvaluationResponse result = evaluation.isCompleted()
                ? ResumeEvaluationResponse.from(evaluation)
                : null;

        return new ResumeEvaluationDetailResponse(
                evaluation.getId(),
                evaluation.getState(),
                evaluation.getResume(),
                evaluation.getPortfolio(),
                evaluation.getJobPosition(),
                evaluation.getJobDescription(),
                evaluation.getJobCareer(),
                result,
                evaluation.getCreatedAt()
        );
    }
}
