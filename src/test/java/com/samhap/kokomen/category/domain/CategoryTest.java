package com.samhap.kokomen.category.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void 기술_카테고리만_조회하면_인성_면접이_제외된다() {
        assertThat(Category.findStackCategories())
                .doesNotContain(Category.PERSONALITY)
                .hasSize(Category.getCategories().size() - 1);
    }

    @Test
    void 인성_면접은_기술_카테고리가_아니다() {
        assertThat(Category.PERSONALITY.isStack()).isFalse();
    }

    @Test
    void 인성_면접을_제외한_모든_카테고리는_기술_카테고리다() {
        assertThat(Category.getCategories())
                .filteredOn(category -> category != Category.PERSONALITY)
                .allMatch(Category::isStack);
    }
}
