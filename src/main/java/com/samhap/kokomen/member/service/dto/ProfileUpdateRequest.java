package com.samhap.kokomen.member.service.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
        String nickname
) {
}
