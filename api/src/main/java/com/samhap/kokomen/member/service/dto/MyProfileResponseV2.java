package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.domain.Member;

public record MyProfileResponseV2(
        Long id,
        String nickname,
        Integer score,
        Long totalMemberCount,
        Long rank,
        Integer freeTokenCount,
        Integer paidTokenCount,
        Boolean profileCompleted
) {
    public MyProfileResponseV2(Member member, Long totalMemberCount, Long rank, Integer freeTokenCount, Integer paidTokenCount) {
        this(member.getId(), member.getNickname(), member.getScore(), totalMemberCount, rank, freeTokenCount, paidTokenCount, member.getProfileCompleted());
    }
}
