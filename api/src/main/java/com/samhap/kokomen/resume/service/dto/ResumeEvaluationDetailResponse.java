package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.domain.ResumeEvaluationState;
import java.time.LocalDateTime;

public record ResumeEvaluationDetailResponse(
        Long id,
        ResumeEvaluationState state,
        ResumeInfo resume,
        PortfolioInfo portfolio,
        String jobPosition,
        String jobDescription,
        String jobCareer,
        ResumeEvaluationResponse result,
        LocalDateTime createdAt
) {
    public static ResumeEvaluationDetailResponse from(ResumeEvaluation evaluation) {
        return new ResumeEvaluationDetailResponse(
                evaluation.getId(),
                evaluation.getState(),
                createResumeInfo(evaluation.getMemberResume()),
                createPortfolioInfo(evaluation.getMemberPortfolio()),
                evaluation.getJobPosition(),
                evaluation.getJobDescription(),
                evaluation.getJobCareer(),
                createResult(evaluation),
                evaluation.getCreatedAt()
        );
    }

    private static ResumeEvaluationResponse createResult(ResumeEvaluation evaluation) {
        if (!evaluation.isCompleted()) {
            return null;
        }
        return ResumeEvaluationResponse.from(evaluation);
    }

    private static ResumeInfo createResumeInfo(MemberResume memberResume) {
        if (memberResume == null) {
            return null;
        }
        return new ResumeInfo(memberResume.getId(), memberResume.getTitle());
    }

    private static PortfolioInfo createPortfolioInfo(MemberPortfolio memberPortfolio) {
        if (memberPortfolio == null) {
            return null;
        }
        return new PortfolioInfo(memberPortfolio.getId(), memberPortfolio.getTitle());
    }
}
