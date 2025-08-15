package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.InterviewMode;
import jakarta.validation.constraints.NotNull;

public record InterviewRequest(
        @NotNull(message = "category는 null일 수 없습니다.")
        Category category,
        @NotNull(message = "max_question_count는 null일 수 없습니다.")
        Integer maxQuestionCount,
        @NotNull(message = "mode는 null일 수 없습니다.")
        InterviewMode mode
) {
}
