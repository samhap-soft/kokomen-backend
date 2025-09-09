package com.samhap.kokomen.auth.service.dto;

public record GoogleLoginRequest(
        String code,
        String redirectUri
) {
}