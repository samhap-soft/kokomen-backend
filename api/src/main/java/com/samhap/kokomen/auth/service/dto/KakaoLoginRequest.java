package com.samhap.kokomen.auth.service.dto;

public record KakaoLoginRequest(
        String code,
        String redirectUri
) {
}
