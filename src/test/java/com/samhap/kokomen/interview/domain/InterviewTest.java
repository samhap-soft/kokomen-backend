package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import org.junit.jupiter.api.Test;

class InterviewTest {

    @Test
    void 라이브_코테_인터뷰는_isLiveCoding이_true이고_isResumeBased는_false이다() {
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.LIVE_CODING)
                .build();

        assertThat(interview.isLiveCoding()).isTrue();
        assertThat(interview.isResumeBased()).isFalse();
    }

    @Test
    void 라이브_코테_인터뷰는_재사용한_루트_질문의_카테고리와_내용을_그대로_노출한다() {
        RootQuestion codingRootQuestion = RootQuestionFixtureBuilder.builder()
                .category(Category.LIVE_CODING)
                .content("주어진 정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요.")
                .build();
        Interview interview = InterviewFixtureBuilder.builder()
                .rootQuestion(codingRootQuestion)
                .interviewType(InterviewType.LIVE_CODING)
                .build();

        assertThat(interview.getDisplayCategory()).isEqualTo(Category.LIVE_CODING.getTitle());
        assertThat(interview.getDisplayQuestion())
                .isEqualTo("주어진 정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요.");
    }

    @Test
    void 카테고리_기반_인터뷰는_isLiveCoding이_false이다() {
        Interview interview = InterviewFixtureBuilder.builder()
                .interviewType(InterviewType.CATEGORY_BASED)
                .build();

        assertThat(interview.isLiveCoding()).isFalse();
    }
}
