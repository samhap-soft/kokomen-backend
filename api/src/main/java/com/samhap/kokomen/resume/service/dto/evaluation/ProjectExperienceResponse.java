package com.samhap.kokomen.resume.service.dto.evaluation;

public record ProjectExperienceResponse(
        int score,
        String reason,
        String improvements
) {
}
