package com.samhap.kokomen.resume.service.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ResumeSaveRequest(
        @NotNull(message = "resume는 null일 수 없습니다.")
        MultipartFile resume,
        MultipartFile portfolio
) {
}
