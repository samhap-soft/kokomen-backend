package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;

public class ResumeAnalysisSourceTextFixtureBuilder {

    private static final String DEFAULT_RESUME_CONTENT = "이력서 원문 텍스트입니다.";

    private ResumeAnalysis analysis;
    private String resumeContent;
    private String portfolioContent;

    public static ResumeAnalysisSourceTextFixtureBuilder builder() {
        return new ResumeAnalysisSourceTextFixtureBuilder();
    }

    public ResumeAnalysisSourceTextFixtureBuilder analysis(ResumeAnalysis analysis) {
        this.analysis = analysis;
        return this;
    }

    public ResumeAnalysisSourceTextFixtureBuilder resumeContent(String resumeContent) {
        this.resumeContent = resumeContent;
        return this;
    }

    public ResumeAnalysisSourceTextFixtureBuilder portfolioContent(String portfolioContent) {
        this.portfolioContent = portfolioContent;
        return this;
    }

    public ResumeAnalysisSourceText build() {
        return new ResumeAnalysisSourceText(
                analysis,
                resumeContent != null ? resumeContent : DEFAULT_RESUME_CONTENT,
                portfolioContent
        );
    }
}
