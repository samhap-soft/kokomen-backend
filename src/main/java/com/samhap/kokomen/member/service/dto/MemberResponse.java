package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.member.domain.Member;

public record MemberResponse(
        Long id,
        String nickname
) {
    public MemberResponse(Member member) {
        this(member.getId(), member.getNickname());
    }
}
