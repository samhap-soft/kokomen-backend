package com.samhap.kokomen.auth.external.dto;

public record KakaoUserInfoResponse(
        Long id,
        KakaoAccount kakaoAccount
) {
}
