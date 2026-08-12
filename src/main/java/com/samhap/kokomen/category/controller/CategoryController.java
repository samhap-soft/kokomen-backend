package com.samhap.kokomen.category.controller;

import com.samhap.kokomen.category.service.CategoryService;
import com.samhap.kokomen.category.service.dto.CategoryResponse;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findCategories(
            @Authentication(required = false) MemberAuth memberAuth
    ) {
        return ResponseEntity.ok(categoryService.findCategories(memberAuth));
    }
}
