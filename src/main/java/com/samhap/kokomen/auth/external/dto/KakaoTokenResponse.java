package com.samhap.kokomen.auth.external.dto;

public record KakaoTokenResponse(
        String accessToken,
        String tokenType,
        String refreshToken,
        Long expiresIn,
        String scope,
        Long refreshTokenExpiresIn
) {
}
