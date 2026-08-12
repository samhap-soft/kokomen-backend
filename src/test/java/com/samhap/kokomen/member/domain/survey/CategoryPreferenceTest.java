package com.samhap.kokomen.member.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryPreferenceTest {

    @Test
    void 직접_고른_관심_분야가_직군_연관_카테고리보다_앞선다() {
        CategoryPreference preference = new CategoryPreference(CareerGoal.BACKEND, List.of(Category.REACT));

        List<Category> sorted = preference.sortByPreference(Category.getCategories());

        assertThat(sorted).containsExactly(
                Category.REACT,
                Category.ALGORITHM_DATA_STRUCTURE,
                Category.DATABASE,
                Category.NETWORK,
                Category.OPERATING_SYSTEM,
                Category.JAVA_SPRING,
                Category.INFRA,
                Category.FRONTEND,
                Category.JAVASCRIPT_TYPESCRIPT,
                Category.PERSONALITY
        );
    }

    @Test
    void 관심_분야와_직군에_모두_해당하면_가장_앞선다() {
        CategoryPreference preference = new CategoryPreference(CareerGoal.BACKEND,
                List.of(Category.JAVA_SPRING, Category.DATABASE, Category.JAVASCRIPT_TYPESCRIPT));

        List<Category> sorted = preference.sortByPreference(Category.getCategories());

        assertThat(sorted).containsExactly(
                Category.DATABASE,
                Category.JAVA_SPRING,
                Category.JAVASCRIPT_TYPESCRIPT,
                Category.ALGORITHM_DATA_STRUCTURE,
                Category.NETWORK,
                Category.OPERATING_SYSTEM,
                Category.INFRA,
                Category.FRONTEND,
                Category.REACT,
                Category.PERSONALITY
        );
    }

    @Test
    void 동점인_카테고리는_기존_선언_순서를_유지한다() {
        CategoryPreference preference = new CategoryPreference(CareerGoal.EXPLORING, List.of(Category.NETWORK));

        List<Category> sorted = preference.sortByPreference(Category.getCategories());

        assertThat(sorted.get(0)).isEqualTo(Category.NETWORK);
        assertThat(sorted.subList(1, sorted.size())).containsExactly(
                Category.ALGORITHM_DATA_STRUCTURE,
                Category.DATABASE,
                Category.OPERATING_SYSTEM,
                Category.JAVA_SPRING,
                Category.INFRA,
                Category.FRONTEND,
                Category.REACT,
                Category.JAVASCRIPT_TYPESCRIPT,
                Category.PERSONALITY
        );
    }

    @Test
    void 대응_카테고리가_없는_목표는_관심_분야만_반영한다() {
        CategoryPreference mobilePreference = new CategoryPreference(CareerGoal.MOBILE, List.of(Category.INFRA));
        CategoryPreference exploringPreference = new CategoryPreference(CareerGoal.EXPLORING, List.of(Category.INFRA));

        assertThat(mobilePreference.sortByPreference(Category.getCategories()))
                .isEqualTo(exploringPreference.sortByPreference(Category.getCategories()));
    }

    @Test
    void 정렬해도_카테고리가_누락되거나_중복되지_않는다() {
        CategoryPreference preference = new CategoryPreference(CareerGoal.FRONTEND, List.of(Category.REACT));

        List<Category> sorted = preference.sortByPreference(Category.getCategories());

        assertThat(sorted).containsExactlyInAnyOrderElementsOf(Category.getCategories());
    }

    @Test
    void 원본_목록을_변경하지_않는다() {
        List<Category> original = Category.getCategories();
        CategoryPreference preference = new CategoryPreference(CareerGoal.BACKEND, List.of(Category.JAVA_SPRING));

        preference.sortByPreference(original);

        assertThat(original).isEqualTo(Category.getCategories());
        assertThat(original.get(0)).isEqualTo(Category.ALGORITHM_DATA_STRUCTURE);
    }
}
