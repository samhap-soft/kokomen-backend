package com.samhap.kokomen.category.service.dto;

import com.samhap.kokomen.category.domain.Category;
import java.util.List;

public record CategoryResponse(
        List<Category> categories
) {
}
