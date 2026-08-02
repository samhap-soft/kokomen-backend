package com.samhap.kokomen.resume.domain;

public record ResumeAnalysisJobInput(String jobPosition, String jobDescription, String jobCareer) {

    public boolean hasJobDescription() {
        return jobDescription != null && !jobDescription.isBlank();
    }
}
