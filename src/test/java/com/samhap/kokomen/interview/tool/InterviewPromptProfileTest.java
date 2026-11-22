package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.interview.domain.InterviewType;
import org.junit.jupiter.api.Test;

class InterviewPromptProfileTest {

    @Test
    void 시스템_설계_면접은_꼬리_질문_프롬프트_프로파일을_쓰지_않는다() {
        assertThatThrownBy(() -> InterviewPromptProfile.from(InterviewType.SYSTEM_DESIGN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시스템 설계 면접은 꼬리 질문 프롬프트를 쓰지 않습니다.");
    }
}
