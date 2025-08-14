package com.samhap.kokomen.member.service.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record ProfileUpdateRequest(
        @Length(max = 255, message = "닉네임은 최대 255자까지 입력할 수 있습니다.")
        @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
        String nickname
) {
}
