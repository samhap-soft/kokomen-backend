package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.List;

public record DimensionScore(int score, List<String> reason, List<String> improvements) {

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 100;

    public DimensionScore {
        validateScore(score);
        validateNotNull(reason, "평가 이유");
        validateBullets(improvements, "보완 사항");
        reason = List.copyOf(reason);
        improvements = List.copyOf(improvements);
    }

    private static void validateScore(int score) {
        if (score < SCORE_MIN || score > SCORE_MAX) {
            throw new ExternalApiException("이력서 분석 차원 점수는 0에서 100 사이여야 합니다. score=" + score);
        }
    }

    /**
     * 평가 이유는 빈 리스트를 허용한다. 평가결과 렌더러가 근거 없는 차원을 "(없음)"으로 렌더하기 때문이며,
     * 최소 개수 강제는 툴 스키마의 minItems와 improvements의 non-empty 검증이 담당한다.
     */
    private static void validateNotNull(List<String> bullets, String fieldName) {
        if (bullets == null) {
            throw new ExternalApiException("이력서 분석 차원의 " + fieldName + "이 null입니다.");
        }
    }

    private static void validateBullets(List<String> bullets, String fieldName) {
        if (bullets == null || bullets.isEmpty()) {
            throw new ExternalApiException("이력서 분석 차원의 " + fieldName + "이 비어 있습니다.");
        }
    }
}
