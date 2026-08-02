package com.samhap.kokomen.interview.service.dto.resumeanalysis;

import com.samhap.kokomen.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotNull;

public record ResumeAnalysisInterviewStartRequest(
        @NotNull(message = "질문 ID는 필수입니다.")
        Long generatedQuestionId,

        @NotNull(message = "최대 질문 개수는 필수입니다.")
        Integer maxQuestionCount,

        @NotNull(message = "면접 모드는 필수입니다.")
        InterviewMode mode
) {
}
