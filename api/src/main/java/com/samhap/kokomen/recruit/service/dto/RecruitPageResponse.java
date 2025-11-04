package com.samhap.kokomen.recruit.service.dto;

import java.util.List;

public record RecruitPageResponse(
        List<RecruitSummaryResponse> data,
        int currentPage,
        int totalPages,
        boolean hasNext
) {
}
