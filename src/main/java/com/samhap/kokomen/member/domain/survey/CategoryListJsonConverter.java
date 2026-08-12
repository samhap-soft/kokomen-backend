package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.persistence.EnumListJsonConverter;
import jakarta.persistence.Converter;

@Converter
public class CategoryListJsonConverter extends EnumListJsonConverter<Category> {

    public CategoryListJsonConverter() {
        super(Category.class);
    }
}
