package com.samhap.kokomen.member.service.dto;

import java.util.List;

public record RankingPageResponse(
        List<RankingResponse> data,
        int currentPage,
        long totalRankingCount,
        int totalPages,
        boolean hasNext
) {
    public static RankingPageResponse of(
            List<RankingResponse> data,
            int currentPage,
            int pageSize,
            long totalRankingCount
    ) {
        int totalPages = (int) Math.ceil((double) totalRankingCount / pageSize);
        boolean hasNext = (currentPage + 1) < totalPages;

        return new RankingPageResponse(
                data,
                currentPage,
                totalRankingCount,
                totalPages,
                hasNext
        );
    }
}
