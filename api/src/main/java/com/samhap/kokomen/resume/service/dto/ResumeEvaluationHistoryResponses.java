package com.samhap.kokomen.resume.service.dto;

import java.util.List;

public record ResumeEvaluationHistoryResponses(
        List<ResumeEvaluationHistoryResponse> evaluations,
        int currentPage,
        long totalResumeEvaluationCount,
        int totalPages,
        boolean hasNext
) {
    public static ResumeEvaluationHistoryResponses of(
            List<ResumeEvaluationHistoryResponse> evaluations,
            int currentPage,
            int pageSize,
            long totalResumeEvaluationCount
    ) {
        int totalPages = (int) Math.ceil((double) totalResumeEvaluationCount / pageSize);
        boolean hasNext = (currentPage + 1) < totalPages;

        return new ResumeEvaluationHistoryResponses(
                evaluations,
                currentPage,
                totalResumeEvaluationCount,
                totalPages,
                hasNext
        );
    }
}
