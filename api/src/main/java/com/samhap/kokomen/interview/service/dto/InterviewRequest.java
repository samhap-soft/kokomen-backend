package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.InterviewMode;

public record InterviewRequest(
        Category category,
        int maxQuestionCount,
        InterviewMode mode
) {
}
