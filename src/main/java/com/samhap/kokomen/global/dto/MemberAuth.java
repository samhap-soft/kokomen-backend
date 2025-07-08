package com.samhap.kokomen.global.dto;

public record MemberAuth(
        Long memberId
) {

    public boolean isAuthenticated() {
        return memberId != null;
    }
}
