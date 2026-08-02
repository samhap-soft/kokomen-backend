package com.samhap.kokomen.resume.service.dto;

public record ResumeAnalysisUsageStatusResponse(
        boolean firstUseFree,
        int tokenCost
) {
}
