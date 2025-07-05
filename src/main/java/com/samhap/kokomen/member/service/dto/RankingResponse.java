package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.domain.Member;

public record RankingResponse(
        Long id,
        String nickname,
        Integer score,
        Integer interviewCount
) {
    public RankingResponse(Member member, Long interviewCount) {
        this(member.getId(), member.getNickname(), member.getScore(), interviewCount.intValue());
    }
}
