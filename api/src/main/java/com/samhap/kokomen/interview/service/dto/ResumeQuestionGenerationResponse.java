package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import java.time.LocalDateTime;

public record ResumeQuestionGenerationResponse(
        Long id,
        String jobCareer,
        ResumeQuestionGenerationState state,
        LocalDateTime createdAt,
        ResumeInfo resume,
        PortfolioInfo portfolio
) {

    public static ResumeQuestionGenerationResponse from(ResumeQuestionGeneration generation) {
        return new ResumeQuestionGenerationResponse(
                generation.getId(),
                generation.getJobCareer(),
                generation.getState(),
                generation.getCreatedAt(),
                ResumeInfo.fromNullable(generation.getMemberResume()),
                PortfolioInfo.fromNullable(generation.getMemberPortfolio())
        );
    }
}
