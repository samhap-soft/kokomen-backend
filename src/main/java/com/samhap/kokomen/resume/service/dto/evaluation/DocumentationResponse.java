package com.samhap.kokomen.resume.service.dto.evaluation;

public record DocumentationResponse(
        int score,
        String reason,
        String improvements
) {
}
