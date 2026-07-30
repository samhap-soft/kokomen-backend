package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.List;

public final class ResumeAnalysisEvaluationFixture {

    private ResumeAnalysisEvaluationFixture() {
    }

    public static ResumeAnalysisEvaluation withJd() {
        return of(true);
    }

    public static ResumeAnalysisEvaluation withoutJd() {
        return of(false);
    }

    /**
     * 90/80/70/60(+JD 50) 고정. JD 없음 = 90*0.30 + 80*0.30 + 70*0.30 + 60*0.10 = 78,
     * JD 있음 = 90*0.25 + 80*0.25 + 70*0.25 + 60*0.10 + 50*0.15 = 73.5 → 74.
     * 두 값이 달라야 4지표·5지표 가중치 세트를 테스트가 구분할 수 있다.
     */
    public static ResumeAnalysisEvaluation of(boolean jdProvided) {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                dimension(90), dimension(80), dimension(70), dimension(60),
                jdProvided ? dimension(50) : null, null, "종합 총평");
        return evaluation.withTotalScore(ResumeAnalysisWeights.of(jdProvided).calculateTotalScore(evaluation));
    }

    public static DimensionScore dimension(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }
}
