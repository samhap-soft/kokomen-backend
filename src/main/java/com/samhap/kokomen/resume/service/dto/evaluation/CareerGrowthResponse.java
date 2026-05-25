package com.samhap.kokomen.resume.service.dto.evaluation;

public record CareerGrowthResponse(
        int score,
        String reason,
        String improvements
) {
}
