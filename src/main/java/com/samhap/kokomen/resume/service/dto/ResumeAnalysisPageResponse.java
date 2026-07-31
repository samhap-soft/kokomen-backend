package com.samhap.kokomen.resume.service.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ResumeAnalysisPageResponse(
        List<ResumeAnalysisSummaryResponse> data,
        int currentPage,
        long totalCount,
        int totalPages,
        boolean hasNext
) {

    public static ResumeAnalysisPageResponse of(List<ResumeAnalysisSummaryResponse> data, Page<?> page) {
        return new ResumeAnalysisPageResponse(data, page.getNumber(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
}
