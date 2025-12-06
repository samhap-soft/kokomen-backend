package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotBlank;

public record ResumeEvaluationAsyncRequest(
        @NotBlank
        String resume,

        String portfolio,

        @NotBlank
        String jobPosition,

        String jobDescription,

        @NotBlank
        String jobCareer
) {
    public ResumeEvaluationRequest toEvaluationRequest() {
        return new ResumeEvaluationRequest(resume, portfolio, jobPosition, jobDescription, jobCareer);
    }
}
