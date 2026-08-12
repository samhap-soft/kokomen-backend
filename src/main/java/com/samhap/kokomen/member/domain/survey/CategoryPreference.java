package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.category.domain.Category;
import java.util.Comparator;
import java.util.List;

/**
 * 온보딩 설문의 career_goal과 tech_topics로 카테고리 선호 순서를 계산한다. 직접 고른 tech_topics가 career_goal 추론보다 강한 신호이므로 더 높은 점수를 준다.
 */
public record CategoryPreference(
        CareerGoal careerGoal,
        List<Category> techTopics
) {

    private static final int TECH_TOPIC_SCORE = 2;
    private static final int CAREER_GOAL_SCORE = 1;

    /**
     * 점수 내림차순으로 정렬한다. Stream.sorted는 안정 정렬이라 동점인 카테고리는 입력 순서(Category 선언 순서)를 유지한다.
     */
    public List<Category> sortByPreference(List<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparingInt(this::scoreOf).reversed())
                .toList();
    }

    private int scoreOf(Category category) {
        int score = 0;
        if (techTopics.contains(category)) {
            score += TECH_TOPIC_SCORE;
        }
        if (careerGoal.isRelatedTo(category)) {
            score += CAREER_GOAL_SCORE;
        }
        return score;
    }
}
