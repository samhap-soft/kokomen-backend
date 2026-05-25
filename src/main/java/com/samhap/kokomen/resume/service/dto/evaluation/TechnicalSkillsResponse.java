package com.samhap.kokomen.resume.service.dto.evaluation;

public record TechnicalSkillsResponse(
        int score,
        String reason,
        String improvements
) {
}
