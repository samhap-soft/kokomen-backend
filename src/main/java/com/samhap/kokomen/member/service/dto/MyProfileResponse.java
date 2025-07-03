package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.domain.Member;

public record MyProfileResponse(
        Long id,
        String nickname,
        Integer score,
        Integer tokenCount
) {
    public MyProfileResponse(Member member) {
        this(member.getId(), member.getNickname(), member.getScore(), member.getFreeTokenCount());
    }
}
