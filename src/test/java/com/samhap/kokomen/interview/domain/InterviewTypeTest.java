package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterviewTypeTest {

    @Test
    void 시스템_설계_면접은_꼬리_질문_흐름이_아니다() {
        assertThat(InterviewType.SYSTEM_DESIGN.isTailQuestionFlow()).isFalse();
    }

    @Test
    void 꼬리_질문_흐름_타입은_기존_네_타입이다() {
        assertThat(InterviewType.TAIL_QUESTION_FLOW_TYPES).containsExactlyInAnyOrder(
                InterviewType.CATEGORY_BASED,
                InterviewType.RESUME_BASED,
                InterviewType.LIVE_CODING,
                InterviewType.PERSONALITY);
    }

    @Test
    void 기존_네_타입은_꼬리_질문_흐름이다() {
        assertThat(InterviewType.TAIL_QUESTION_FLOW_TYPES)
                .allSatisfy(interviewType -> assertThat(interviewType.isTailQuestionFlow()).isTrue());
    }
}
