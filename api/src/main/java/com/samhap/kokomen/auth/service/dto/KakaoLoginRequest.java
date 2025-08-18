package com.samhap.kokomen.auth.service.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank(message = "code는 비어있을 수 없습니다.")
        String code,
        @NotBlank(message = "redirect_uri는 비어있을 수 없습니다.")
        String redirectUri
) {
}
