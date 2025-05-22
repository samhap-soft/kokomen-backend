package com.samhap.kokomen.category.domain;

import java.util.List;

public enum Category {
    ALGORITHM,
    DATA_STRUCTURE,
    DATABASE,
    NETWORK,
    OPERATING_SYSTEM,
    ;

    private static final List<Category> CATEGORIES = List.of(values());

    public static List<Category> getCategories() {
        return CATEGORIES;
    }
}
