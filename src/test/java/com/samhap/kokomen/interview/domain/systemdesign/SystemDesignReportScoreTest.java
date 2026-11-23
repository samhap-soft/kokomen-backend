package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;
import static com.samhap.kokomen.answer.domain.AnswerRank.D;
import static com.samhap.kokomen.answer.domain.AnswerRank.F;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemDesignReportScoreTest {

    // 100 × (0.5 × 3/4 + 0.5 × (0.75 + 0.75 + 0.5 + 1.0 + 0.75 + 0.5) / 6) = 72.9 → 73
    @Test
    void 평가_포인트_4개_중_3개를_충족하고_등급이_B_B_C_A_B_C면_73점이다() {
        assertThat(SystemDesignReportScore.calculate(3, 4, List.of(B, B, C, A, B, C))).isEqualTo(73);
    }

    @Test
    void 모두_충족하고_모두_A면_100점이다() {
        assertThat(SystemDesignReportScore.calculate(4, 4, Collections.nCopies(6, A))).isEqualTo(100);
    }

    @Test
    void 아무것도_충족하지_않고_모두_F면_0점이다() {
        assertThat(SystemDesignReportScore.calculate(0, 4, Collections.nCopies(6, F))).isZero();
    }

    // 100 × (0.5 × 3/5 + 0.5 × (1.0 + 0.75 + 0.5 + 0.25 + 0 + 1.0) / 6) = 59.2 → 59
    @Test
    void 평가_포인트가_5개여도_계산한다() {
        assertThat(SystemDesignReportScore.calculate(3, 5, List.of(A, B, C, D, F, A))).isEqualTo(59);
    }

    // 100 × (0.5 × 2/5 + 0.5 × (1.0 × 3 + 0.75 × 2 + 0) / 6) = 57.5. double로 그대로 계산하면 57.49999…가 되어 57로 내려간다.
    @Test
    void 반올림_경계값은_부동소수점_오차_없이_올린다() {
        assertThat(SystemDesignReportScore.calculate(2, 5, List.of(A, A, A, B, B, F))).isEqualTo(58);
    }

    @Test
    void 등급이_6개가_아니면_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignReportScore.calculate(3, 4, List.of(A, B, C, D, F)))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> SystemDesignReportScore.calculate(3, 4, List.of(A, B, C, D, F, A, B)))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    void 평가_포인트가_0개면_거절한다() {
        assertThatThrownBy(() -> SystemDesignReportScore.calculate(0, 0, Collections.nCopies(6, A)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 충족한_수가_음수이거나_평가_포인트_수보다_많으면_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignReportScore.calculate(-1, 4, Collections.nCopies(6, A)))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> SystemDesignReportScore.calculate(5, 4, Collections.nCopies(6, A)))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }
}
