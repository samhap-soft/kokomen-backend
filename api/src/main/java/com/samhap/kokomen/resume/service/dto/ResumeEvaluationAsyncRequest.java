package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.global.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

public record ResumeEvaluationAsyncRequest(
        MultipartFile resume,
        MultipartFile portfolio,
        Long resumeId,
        Long portfolioId,
        String jobPosition,
        String jobDescription,
        String jobCareer
) {

    public ResumeEvaluationAsyncRequest {
        if (resume == null && resumeId == null) {
            throw new BadRequestException("이력서는 필수 입니다.");
        }
        if (jobPosition == null || jobPosition.isBlank()) {
            throw new BadRequestException("지원 직무는 필수 입니다.");
        }
        if (jobCareer == null || jobCareer.isBlank()) {
            throw new BadRequestException("경력 사항은 필수 입니다.");
        }
    }
}
