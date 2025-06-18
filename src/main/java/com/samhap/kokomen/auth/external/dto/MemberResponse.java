package com.samhap.kokomen.auth.external.dto;

import com.samhap.kokomen.member.domain.Member;

public record MemberResponse(
        Long id
) {
    public MemberResponse(Member member) {
        this(member.getId());
    }
}
