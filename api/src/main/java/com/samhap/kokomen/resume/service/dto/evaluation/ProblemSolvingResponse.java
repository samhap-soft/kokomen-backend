package com.samhap.kokomen.resume.service.dto.evaluation;

public record ProblemSolvingResponse(
        int score,
        String reason,
        String improvements
) {
}
