package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.domain.Member;

public record MyProfileResponse(
        Long id,
        String nickname,
        Integer score,
        Long totalMemberCount,
        Long rank,
        Integer tokenCount,
        Boolean profileCompleted
) {
    public MyProfileResponse(Member member, Long totalMemberCount, Long rank) {
        this(member.getId(), member.getNickname(), member.getScore(), totalMemberCount, rank, member.getFreeTokenCount(), member.getProfileCompleted());
    }
}
