package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotBlank;

public record ResumeAnalysisClaimRequest(
        @NotBlank(message = "게스트 토큰은 필수입니다.")
        String guestToken
) {
}
