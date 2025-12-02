package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotBlank;

public record ResumeEvaluationRequest(
        @NotBlank
        String resume,

        String portfolio,

        @NotBlank
        String jobPosition,

        String jobDescription,

        @NotBlank
        String jobCareer
) {
}
