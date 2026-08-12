package com.samhap.kokomen.member.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerGoalTest {

    @Test
    void 백엔드_목표는_백엔드_관련_카테고리와_연관된다() {
        List<Category> related = List.of(Category.JAVA_SPRING, Category.DATABASE, Category.NETWORK,
                Category.OPERATING_SYSTEM, Category.INFRA, Category.ALGORITHM_DATA_STRUCTURE);

        assertThat(related).allMatch(CareerGoal.BACKEND::isRelatedTo);
        assertThat(List.of(Category.FRONTEND, Category.REACT, Category.JAVASCRIPT_TYPESCRIPT))
                .noneMatch(CareerGoal.BACKEND::isRelatedTo);
    }

    @Test
    void 프론트엔드_목표는_프론트엔드_관련_카테고리와_연관된다() {
        assertThat(List.of(Category.FRONTEND, Category.REACT, Category.JAVASCRIPT_TYPESCRIPT))
                .allMatch(CareerGoal.FRONTEND::isRelatedTo);
        assertThat(Category.JAVA_SPRING).matches(category -> !CareerGoal.FRONTEND.isRelatedTo(category));
    }

    @Test
    void AI_데이터_목표는_알고리즘과_데이터베이스와_연관된다() {
        assertThat(List.of(Category.ALGORITHM_DATA_STRUCTURE, Category.DATABASE))
                .allMatch(CareerGoal.AI_DATA::isRelatedTo);
        assertThat(Category.REACT).matches(category -> !CareerGoal.AI_DATA.isRelatedTo(category));
    }

    @Test
    void 대응_카테고리가_없는_목표는_어떤_카테고리와도_연관되지_않는다() {
        List<CareerGoal> unmappedGoals = List.of(CareerGoal.MOBILE, CareerGoal.CAREER_SWITCH, CareerGoal.EXPLORING);

        for (CareerGoal careerGoal : unmappedGoals) {
            assertThat(Category.getCategories()).noneMatch(careerGoal::isRelatedTo);
        }
    }

    @Test
    void 인성_면접은_어떤_목표와도_연관되지_않는다() {
        assertThat(Arrays.asList(CareerGoal.values()))
                .noneMatch(careerGoal -> careerGoal.isRelatedTo(Category.PERSONALITY));
    }
}
