package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.global.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

public record ResumeBasedQuestionGenerateRequest(
        MultipartFile resume,
        MultipartFile portfolio,
        Long resumeId,
        Long portfolioId,
        String jobCareer
) {

    public ResumeBasedQuestionGenerateRequest {
        if ((resume == null || resume.isEmpty()) && resumeId == null) {
            throw new BadRequestException("이력서 파일 또는 이력서 ID는 필수입니다.");
        }
        if (jobCareer == null || jobCareer.isBlank()) {
            throw new BadRequestException("경력 사항은 필수입니다.");
        }
    }
}
