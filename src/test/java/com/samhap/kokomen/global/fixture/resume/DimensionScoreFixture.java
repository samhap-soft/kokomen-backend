package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.resume.domain.DimensionScore;
import java.util.List;

public final class DimensionScoreFixture {

    private DimensionScoreFixture() {
    }

    public static DimensionScore of(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }

    public static DimensionScore of(int score, String reason, String improvement) {
        return new DimensionScore(score, List.of(reason), List.of(improvement));
    }

    public static DimensionScore of(int score, List<String> reason, List<String> improvements) {
        return new DimensionScore(score, reason, improvements);
    }
}
