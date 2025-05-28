package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.category.domain.Category;
import java.util.List;

public record InterviewRequest(
        List<Category> categories
) {
}
