package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeAnalysisState;

public record ResumeAnalysisQuestionRetryResponse(
        Long analysisId,
        ResumeAnalysisState state,
        int questionRetryCount
) {
}
