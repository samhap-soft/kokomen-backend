package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class InterviewModeTest {

    @ValueSource(ints = {Interview.MIN_ALLOWED_MAX_QUESTION_COUNT, Interview.MAX_ALLOWED_MAX_QUESTION_COUNT})
    @ParameterizedTest
    void 텍스트_면접_시작에는_최대_질문_수만큼_토큰이_필요하다(int maxQuestionCount) {
        assertThat(InterviewMode.TEXT.calculateRequiredTokenCountToStart(maxQuestionCount))
                .isEqualTo(maxQuestionCount);
    }

    @ValueSource(ints = {Interview.MIN_ALLOWED_MAX_QUESTION_COUNT, Interview.MAX_ALLOWED_MAX_QUESTION_COUNT})
    @ParameterizedTest
    void 음성_면접_시작에는_첫_질문을_뺀_질문_음성만큼_토큰이_더_필요하다(int maxQuestionCount) {
        assertThat(InterviewMode.VOICE.calculateRequiredTokenCountToStart(maxQuestionCount))
                .isEqualTo(2 * maxQuestionCount - 1);
    }

    @Test
    void 텍스트_답변에는_다음_질문이_있든_없든_토큰_1개가_필요하다() {
        assertThat(InterviewMode.TEXT.calculateRequiredTokenCountToAnswer(true)).isEqualTo(1);
        assertThat(InterviewMode.TEXT.calculateRequiredTokenCountToAnswer(false)).isEqualTo(1);
    }

    @Test
    void 음성_답변에는_다음_질문이_이어질_때만_음성_토큰_1개가_더_필요하다() {
        assertThat(InterviewMode.VOICE.calculateRequiredTokenCountToAnswer(true)).isEqualTo(2);
        assertThat(InterviewMode.VOICE.calculateRequiredTokenCountToAnswer(false)).isEqualTo(1);
    }

    /**
     * 시작 검증이 답변 전 검증의 합보다 적으면 마지막 답변에서 막히고, 많으면 쓰지 않을 토큰까지 요구한다.
     */
    @EnumSource(InterviewMode.class)
    @ParameterizedTest
    void 시작에_필요한_토큰은_마지막_답변까지_각_답변에_필요한_토큰을_모두_더한_값과_같다(InterviewMode mode) {
        for (int maxQuestionCount = Interview.MIN_ALLOWED_MAX_QUESTION_COUNT;
             maxQuestionCount <= Interview.MAX_ALLOWED_MAX_QUESTION_COUNT; maxQuestionCount++) {
            int requiredTokenCountToAnswerAll = mode.calculateRequiredTokenCountToAnswer(true) * (maxQuestionCount - 1)
                    + mode.calculateRequiredTokenCountToAnswer(false);

            assertThat(mode.calculateRequiredTokenCountToStart(maxQuestionCount))
                    .isEqualTo(requiredTokenCountToAnswerAll);
        }
    }
}
