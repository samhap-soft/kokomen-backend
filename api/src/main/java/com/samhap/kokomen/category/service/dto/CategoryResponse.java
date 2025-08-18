package com.samhap.kokomen.category.service.dto;

import com.samhap.kokomen.category.domain.Category;

public record CategoryResponse(
        String key,
        String title,
        String description,
        String imageUrl
) {
    public CategoryResponse(Category category) {
        this(
                category.name(),
                category.getTitle(),
                category.getDescription(),
                category.getImageUrl()
        );
    }
}
