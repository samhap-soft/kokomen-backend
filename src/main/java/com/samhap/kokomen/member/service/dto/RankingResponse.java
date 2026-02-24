package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.repository.dto.RankingProjection;
import java.util.List;

public record RankingResponse(
        Long id,
        String nickname,
        Integer score,
        Integer finishedInterviewCount
) {
    public RankingResponse(com.samhap.kokomen.member.repository.dto.RankingProjection rankingProjection) {
        this(
                rankingProjection.getId(),
                rankingProjection.getNickname(),
                rankingProjection.getScore(),
                rankingProjection.getFinishedInterviewCount().intValue()
        );
    }

    public static List<RankingResponse> createRankingResponses(List<RankingProjection> rankingProjections) {
        return rankingProjections.stream()
                .map(RankingResponse::new)
                .toList();
    }
}
