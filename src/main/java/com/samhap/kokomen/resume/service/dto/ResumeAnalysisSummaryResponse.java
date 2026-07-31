package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.dto.ResumeAnalysisSummaryProjection;
import java.time.LocalDateTime;

public record ResumeAnalysisSummaryResponse(
        Long analysisId,
        ResumeAnalysisState state,
        String jobPosition,
        String jobCareer,
        boolean jdProvided,
        Integer totalScore,
        Integer questionCount,
        LocalDateTime createdAt
) {

    public static ResumeAnalysisSummaryResponse of(ResumeAnalysisSummaryProjection projection, int questionCount) {
        return new ResumeAnalysisSummaryResponse(
                projection.getId(),
                projection.getState(),
                projection.getJobPosition(),
                projection.getJobCareer(),
                projection.isJdProvided(),
                projection.getTotalScore(),
                questionCount,
                projection.getCreatedAt());
    }
}
