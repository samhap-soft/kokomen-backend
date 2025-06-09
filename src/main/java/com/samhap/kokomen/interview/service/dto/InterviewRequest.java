package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.category.domain.Category;

public record InterviewRequest(
        Category category,
        int maxQuestionCount
) {
}
