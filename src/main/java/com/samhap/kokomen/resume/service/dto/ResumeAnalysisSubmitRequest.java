package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import org.springframework.web.multipart.MultipartFile;

public record ResumeAnalysisSubmitRequest(
        MultipartFile resume,
        MultipartFile portfolio,
        Long resumeId,
        Long portfolioId,
        String jobPosition,
        String jobDescription,
        String jobCareer
) {

    private static final int JOB_POSITION_MAX_LENGTH = 500;
    private static final int JOB_DESCRIPTION_MAX_LENGTH = 10_000;
    private static final int JOB_CAREER_MAX_LENGTH = 100;

    // 메시지를 fieldName + "는 필수입니다." 식으로 조립하지 않는다. "경력 사항는 필수입니다."로 조사가 깨진다.
    public ResumeAnalysisSubmitRequest {
        if (!isPresent(resume) && resumeId == null) {
            throw new BadRequestException("이력서 파일 또는 이력서 ID는 필수입니다.");
        }
        validateRequired(jobPosition, "지원 직무는 필수입니다.");
        validateMaxLength(jobPosition, JOB_POSITION_MAX_LENGTH, "지원 직무는 500자를 초과할 수 없습니다.");
        validateRequired(jobCareer, "경력 사항은 필수입니다.");
        validateMaxLength(jobCareer, JOB_CAREER_MAX_LENGTH, "경력 사항은 100자를 초과할 수 없습니다.");
        validateMaxLength(jobDescription, JOB_DESCRIPTION_MAX_LENGTH, "채용 공고는 10000자를 초과할 수 없습니다.");
    }

    private static void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
    }

    private static void validateMaxLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BadRequestException(message);
        }
    }

    private static boolean isPresent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    public boolean hasResumeFile() {
        return isPresent(resume);
    }

    public boolean hasPortfolioFile() {
        return isPresent(portfolio);
    }

    public boolean hasSavedMaterialId() {
        return resumeId != null || portfolioId != null;
    }

    public boolean isJdProvided() {
        return jobDescription != null && !jobDescription.isBlank();
    }

    public ResumeAnalysisJobInput toJobInput() {
        return new ResumeAnalysisJobInput(jobPosition, isJdProvided() ? jobDescription : null, jobCareer);
    }
}
