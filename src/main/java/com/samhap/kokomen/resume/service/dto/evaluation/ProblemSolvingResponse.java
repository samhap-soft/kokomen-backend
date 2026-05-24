package com.samhap.kokomen.resume.service.dto.evaluation;

import java.util.List;

public record ProblemSolvingResponse(
        int score,
        List<String> reason,
        List<String> improvements
) {
}
