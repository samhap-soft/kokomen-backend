package com.samhap.kokomen.resume.service.dto;

public record TextExtractionResult(
        String resumeText,
        String portfolioText
) {

    public static TextExtractionResult of(String resumeText, String portfolioText) {
        return new TextExtractionResult(resumeText, portfolioText);
    }

    public boolean hasResumeText() {
        return resumeText != null && !resumeText.isBlank();
    }
}
