package com.samhap.kokomen.category.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.category.service.dto.CategoryResponse;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    public CategoryResponse findCategories() {
        return new CategoryResponse(Category.getCategories());
    }
}
