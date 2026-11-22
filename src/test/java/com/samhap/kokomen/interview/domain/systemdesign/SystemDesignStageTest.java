package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SystemDesignStageTest {

    @Test
    void ofQuestionOrder는_1부터_3은_개략_4부터_6은_심화이고_범위_밖은_거절한다() {
        assertAll(
                () -> assertThat(SystemDesignStage.ofQuestionOrder(1)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(SystemDesignStage.ofQuestionOrder(3)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(SystemDesignStage.ofQuestionOrder(4)).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(SystemDesignStage.ofQuestionOrder(6)).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThatThrownBy(() -> SystemDesignStage.ofQuestionOrder(0))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> SystemDesignStage.ofQuestionOrder(7))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    void 개략_설계_질문_수와_전체_질문_수가_단계_경계를_정한다() {
        assertAll(
                () -> assertThat(SystemDesignStage.ofQuestionOrder(SystemDesignInterview.HIGH_LEVEL_QUESTION_COUNT))
                        .isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(SystemDesignStage.ofQuestionOrder(SystemDesignInterview.HIGH_LEVEL_QUESTION_COUNT + 1))
                        .isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(SystemDesignStage.ofQuestionOrder(SystemDesignInterview.MAX_QUESTION_COUNT))
                        .isEqualTo(SystemDesignStage.DEEP_DIVE)
        );
    }
}
