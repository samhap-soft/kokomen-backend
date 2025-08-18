package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterviewTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 21})
    void 최대_질문_개수가_3미만_20초과라면_예외가_발생한다(int maxQuestionCount) {
        assertThatThrownBy(() -> InterviewFixtureBuilder.builder().maxQuestionCount(maxQuestionCount).build())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("최대 질문 개수는");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 20})
    void 최대_질문_개수가_2이상_20이하라면_인터뷰_생성에_성공한다(int maxQuestionCount) {
        assertThatCode(() -> InterviewFixtureBuilder.builder().maxQuestionCount(maxQuestionCount).build())
                .doesNotThrowAnyException();
    }

    @Test
    void 진행중인_인터뷰를_평가하면_FINSIHED_상태가_된다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder().interviewState(InterviewState.IN_PROGRESS).build();

        // when
        interview.evaluate("더 공부하세요", 0);

        // then
        assertThat(interview.getInterviewState()).isEqualTo(InterviewState.FINISHED);
    }

    @Test
    void 이미_평가된_질문을_다시_평가하면_예외가_발생한다() {
        // given
        Interview interview = InterviewFixtureBuilder.builder().interviewState(InterviewState.FINISHED).build();

        // when & then
        assertThatThrownBy(() -> interview.evaluate("더 공부하세요", 0))
                .isInstanceOf(BadRequestException.class);
    }
}
