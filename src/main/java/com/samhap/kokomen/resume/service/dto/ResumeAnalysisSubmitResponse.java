package com.samhap.kokomen.resume.service.dto;

public record ResumeAnalysisSubmitResponse(
        Long analysisId,
        String guestToken
) {

    public static ResumeAnalysisSubmitResponse ofMember(Long analysisId) {
        return new ResumeAnalysisSubmitResponse(analysisId, null);
    }

    public static ResumeAnalysisSubmitResponse ofGuest(Long analysisId, String guestToken) {
        return new ResumeAnalysisSubmitResponse(analysisId, guestToken);
    }
}
