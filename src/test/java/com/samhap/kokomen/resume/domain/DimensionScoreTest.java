package com.samhap.kokomen.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DimensionScoreTest {

    @Test
    void 점수가_경계값이면_생성된다() {
        assertThat(new DimensionScore(0, List.of("근거"), List.of("보완")).score()).isZero();
        assertThat(new DimensionScore(100, List.of("근거"), List.of("보완")).score()).isEqualTo(100);
    }

    @Test
    void 점수가_100을_넘으면_예외가_발생한다() {
        assertThatThrownBy(() -> new DimensionScore(101, List.of("근거"), List.of("보완")))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 점수가_음수면_예외가_발생한다() {
        assertThatThrownBy(() -> new DimensionScore(-1, List.of("근거"), List.of("보완")))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 평가_이유는_빈_리스트여도_생성된다() {
        // 평가결과 렌더러가 근거 없는 차원을 "(없음)"으로 렌더하는 분기를 실제로 타야 하므로
        // reason에 non-empty 검증을 걸지 않는다. minItems 강제는 tool 스키마와 improvements가 담당한다.
        assertThat(new DimensionScore(80, List.of(), List.of("보완")).reason()).isEmpty();
    }

    @Test
    void 평가_이유가_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> new DimensionScore(80, null, List.of("보완")))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 보완_사항이_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> new DimensionScore(80, List.of("근거"), List.of()))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 보완_사항이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> new DimensionScore(80, List.of("근거"), null))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 생성_후_원본_리스트를_수정해도_값이_바뀌지_않는다() {
        List<String> reason = new ArrayList<>(List.of("근거1"));
        DimensionScore dimensionScore = new DimensionScore(80, reason, List.of("보완1"));

        reason.add("나중에 추가된 근거");

        assertThat(dimensionScore.reason()).containsExactly("근거1");
    }
}
