package com.samhap.kokomen.resume.service.dto;

import java.util.List;

public record ResumeAnalysisDimensionResponse(
        Integer score,
        Double weight,
        List<String> reason,
        List<String> improvements
) {

    public static ResumeAnalysisDimensionResponse fromNullable(Integer score, Double weight,
                                                               List<String> reason, List<String> improvements) {
        if (score == null || weight == null) {
            return null;
        }
        return new ResumeAnalysisDimensionResponse(score, weight, reason, improvements);
    }
}
