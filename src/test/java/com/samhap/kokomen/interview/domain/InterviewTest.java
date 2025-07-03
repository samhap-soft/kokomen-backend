package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
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
}
