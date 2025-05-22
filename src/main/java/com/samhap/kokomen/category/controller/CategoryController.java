package com.samhap.kokomen.category.controller;

import com.samhap.kokomen.category.service.CategoryService;
import com.samhap.kokomen.category.service.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/categories")
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public CategoryResponse findCategories() {
        return categoryService.findCategories();
    }
}
