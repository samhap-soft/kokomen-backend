package com.samhap.kokomen.resume.domain;

import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.JD_FIT;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROBLEM_SOLVING;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROJECT_EXPERIENCE;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.SOFT_SKILLS;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.TECHNICAL_SKILLS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeAnalysisEvaluationTest {

    @Test
    void JD적합성이_있으면_점수_맵은_5개_엔트리이고_선언_순서를_따른다() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(dimensionScore(90), dimensionScore(80),
                dimensionScore(70), dimensionScore(60), dimensionScore(50), null, "종합 총평");

        Map<ResumeAnalysisDimension, Integer> scores = evaluation.scores();

        assertThat(scores).hasSize(5);
        assertThat(scores.keySet()).containsExactly(
                PROBLEM_SOLVING, PROJECT_EXPERIENCE, TECHNICAL_SKILLS, SOFT_SKILLS, JD_FIT);
        assertThat(scores.get(JD_FIT)).isEqualTo(50);
    }

    @Test
    void JD적합성이_없으면_점수_맵은_4개_엔트리다() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(dimensionScore(90), dimensionScore(80),
                dimensionScore(70), dimensionScore(60), null, null, "종합 총평");

        Map<ResumeAnalysisDimension, Integer> scores = evaluation.scores();

        assertThat(scores).hasSize(4);
        assertThat(scores).doesNotContainKey(JD_FIT);
        assertThat(evaluation.jdFit()).isNull();
    }

    @Test
    void withTotalScore는_종합점수만_바꾼_새_값객체를_반환한다() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(dimensionScore(90), dimensionScore(80),
                dimensionScore(70), dimensionScore(60), null, null, "종합 총평");

        ResumeAnalysisEvaluation scored = evaluation.withTotalScore(78);

        assertThat(evaluation.totalScore()).isNull();
        assertThat(scored.totalScore()).isEqualTo(78);
        assertThat(scored.totalFeedback()).isEqualTo("종합 총평");
        assertThat(scored.problemSolving()).isEqualTo(evaluation.problemSolving());
        assertThat(scored.jdFit()).isNull();
    }

    private static DimensionScore dimensionScore(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }
}
