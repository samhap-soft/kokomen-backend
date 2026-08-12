package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.category.domain.Category;
import java.util.List;

public enum CareerGoal {

    BACKEND(List.of(Category.JAVA_SPRING, Category.DATABASE, Category.NETWORK, Category.OPERATING_SYSTEM,
            Category.INFRA, Category.ALGORITHM_DATA_STRUCTURE)),
    FRONTEND(List.of(Category.FRONTEND, Category.REACT, Category.JAVASCRIPT_TYPESCRIPT)),
    AI_DATA(List.of(Category.ALGORITHM_DATA_STRUCTURE, Category.DATABASE)),
    // 대응하는 카테고리가 없는 목표들이다. 이 경우 카테고리 정렬은 tech_topics만 반영한다.
    MOBILE(List.of()),
    CAREER_SWITCH(List.of()),
    EXPLORING(List.of());

    private final List<Category> relatedCategories;

    CareerGoal(List<Category> relatedCategories) {
        this.relatedCategories = relatedCategories;
    }

    public boolean isRelatedTo(Category category) {
        return relatedCategories.contains(category);
    }
}
