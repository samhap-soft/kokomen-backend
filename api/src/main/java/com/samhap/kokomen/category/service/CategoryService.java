package com.samhap.kokomen.category.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.category.service.dto.CategoryResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    public List<CategoryResponse> findCategories() {
        return Category.getCategories()
                .stream()
                .map(CategoryResponse::new)
                .toList();
    }
}
