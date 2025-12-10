package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SavedResumeEvaluationAsyncRequest(
        @NotNull
        Long resumeId,

        Long portfolioId,

        @NotBlank
        String jobPosition,

        String jobDescription,

        @NotBlank
        String jobCareer
) {
}
