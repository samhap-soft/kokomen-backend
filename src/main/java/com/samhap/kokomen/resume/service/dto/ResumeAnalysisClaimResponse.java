package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeAnalysisState;

public record ResumeAnalysisClaimResponse(
        Long analysisId,
        ResumeAnalysisState state
) {
}
