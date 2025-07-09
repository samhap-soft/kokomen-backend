package com.samhap.kokomen.global.dto;

public record MemberAuth(
        Long memberId
) {

    private static final MemberAuth NOT_AUTHENTICATED = new MemberAuth(null);

    public static MemberAuth notAuthenticated() {
        return NOT_AUTHENTICATED;
    }

    public boolean isAuthenticated() {
        return memberId != null;
    }
}
