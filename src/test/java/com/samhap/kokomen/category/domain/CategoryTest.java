package com.samhap.kokomen.category.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void 라이브_코테_카테고리는_일반_카테고리_목록에서_제외된다() {
        List<Category> categories = Category.getCategories();

        assertThat(categories).doesNotContain(Category.LIVE_CODING);
    }

    @Test
    void 라이브_코테_카테고리도_enum_값으로는_존재한다() {
        assertThat(List.of(Category.values())).contains(Category.LIVE_CODING);
    }
}
