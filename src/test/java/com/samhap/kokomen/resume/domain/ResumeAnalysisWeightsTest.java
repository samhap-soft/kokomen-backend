package com.samhap.kokomen.resume.domain;

import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.JD_FIT;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROBLEM_SOLVING;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROJECT_EXPERIENCE;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.SOFT_SKILLS;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.TECHNICAL_SKILLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.resume.tool.ResumeAnalysisPromptFragments;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ResumeAnalysisWeightsTest {

    @Test
    void JD_제공_여부로_가중치_세트를_선택한다() {
        assertThat(ResumeAnalysisWeights.of(true)).isEqualTo(ResumeAnalysisWeights.JD_PROVIDED);
        assertThat(ResumeAnalysisWeights.of(false)).isEqualTo(ResumeAnalysisWeights.JD_ABSENT);
    }

    @Test
    void JD가_제공되면_5지표_가중치의_합은_1이다() {
        ResumeAnalysisWeights weights = ResumeAnalysisWeights.JD_PROVIDED;

        assertThat(weights.dimensions()).hasSize(5);
        assertThat(sumOfWeights(weights)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void JD가_없으면_4지표_가중치의_합은_1이다() {
        ResumeAnalysisWeights weights = ResumeAnalysisWeights.JD_ABSENT;

        assertThat(weights.dimensions()).hasSize(4);
        assertThat(sumOfWeights(weights)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void 각_차원의_가중치_값은_설계에_확정된_2세트와_일치한다() {
        ResumeAnalysisWeights jdProvided = ResumeAnalysisWeights.JD_PROVIDED;
        assertThat(jdProvided.weightOf(PROBLEM_SOLVING)).isEqualTo(0.25);
        assertThat(jdProvided.weightOf(PROJECT_EXPERIENCE)).isEqualTo(0.25);
        assertThat(jdProvided.weightOf(TECHNICAL_SKILLS)).isEqualTo(0.25);
        assertThat(jdProvided.weightOf(SOFT_SKILLS)).isEqualTo(0.10);
        assertThat(jdProvided.weightOf(JD_FIT)).isEqualTo(0.15);

        ResumeAnalysisWeights jdAbsent = ResumeAnalysisWeights.JD_ABSENT;
        assertThat(jdAbsent.weightOf(PROBLEM_SOLVING)).isEqualTo(0.30);
        assertThat(jdAbsent.weightOf(PROJECT_EXPERIENCE)).isEqualTo(0.30);
        assertThat(jdAbsent.weightOf(TECHNICAL_SKILLS)).isEqualTo(0.30);
        assertThat(jdAbsent.weightOf(SOFT_SKILLS)).isEqualTo(0.10);
        assertThat(jdAbsent.weightOf(JD_FIT)).isNull();
    }

    @Test
    void JD_제공_가중치로_종합점수를_계산한다() {
        ResumeAnalysisEvaluation evaluation = evaluationWithJdFit(90, 80, 70, 60, 50);

        int totalScore = ResumeAnalysisWeights.JD_PROVIDED.calculateTotalScore(evaluation);

        // 0.25*90 + 0.25*80 + 0.25*70 + 0.10*60 + 0.15*50 = 22.5 + 20 + 17.5 + 6 + 7.5 = 73.5
        assertThat(totalScore).isEqualTo(74);
    }

    @Test
    void JD_미제공_가중치로_종합점수를_계산한다() {
        ResumeAnalysisEvaluation evaluation = evaluationWithoutJdFit(90, 80, 70, 60);

        int totalScore = ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluation);

        // 0.30*90 + 0.30*80 + 0.30*70 + 0.10*60 = 27 + 24 + 21 + 6 = 78
        assertThat(totalScore).isEqualTo(78);
    }

    @Test
    void JD_미제공에서_JD적합성은_0점으로_취급되지_않는다() {
        ResumeAnalysisEvaluation evaluation = evaluationWithoutJdFit(90, 80, 70, 60);

        int totalScore = ResumeAnalysisWeights.of(false).calculateTotalScore(evaluation);

        // 구 withCalculatedTotalScore의 scoreOf(null) -> 0 버그가 살아 있으면
        // JD 포함 가중치에 jd_fit 0점이 섞여 22.5 + 20 + 17.5 + 6 + 0 = 66이 된다.
        assertThat(totalScore).isEqualTo(78);
        assertThat(totalScore).isNotEqualTo(66);
        assertThat(evaluation.scores()).doesNotContainKey(JD_FIT);
    }

    @Test
    void 가중합의_소수점은_반올림된다() {
        assertThat(ResumeAnalysisWeights.JD_PROVIDED.calculateTotalScore(evaluationWithJdFit(90, 80, 70, 60, 50)))
                .isEqualTo(74);   // 73.5
        assertThat(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluationWithoutJdFit(80, 70, 60, 55)))
                .isEqualTo(69);   // 68.5
    }

    @Test
    void 모든_지표가_100이면_두_세트_모두_100이다() {
        assertThat(ResumeAnalysisWeights.JD_PROVIDED.calculateTotalScore(
                evaluationWithJdFit(100, 100, 100, 100, 100))).isEqualTo(100);
        assertThat(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(
                evaluationWithoutJdFit(100, 100, 100, 100))).isEqualTo(100);
    }

    @Test
    void 모든_지표가_0이면_두_세트_모두_0이다() {
        assertThat(ResumeAnalysisWeights.JD_PROVIDED.calculateTotalScore(
                evaluationWithJdFit(0, 0, 0, 0, 0))).isZero();
        assertThat(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(
                evaluationWithoutJdFit(0, 0, 0, 0))).isZero();
    }

    @Test
    void JD가_제공됐는데_JD적합성_점수가_없으면_예외가_발생한다() {
        ResumeAnalysisEvaluation evaluation = evaluationWithoutJdFit(90, 80, 70, 60);

        assertThatThrownBy(() -> ResumeAnalysisWeights.JD_PROVIDED.calculateTotalScore(evaluation))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void JD가_없는데_JD적합성_점수가_오면_예외가_발생한다() {
        ResumeAnalysisEvaluation evaluation = evaluationWithJdFit(90, 80, 70, 60, 50);

        assertThatThrownBy(() -> ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluation))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void 가중치_세트의_차원_목록은_선언_순서를_유지한다() {
        assertThat(ResumeAnalysisWeights.JD_PROVIDED.dimensions()).containsExactly(
                PROBLEM_SOLVING, PROJECT_EXPERIENCE, TECHNICAL_SKILLS, SOFT_SKILLS, JD_FIT);
        assertThat(ResumeAnalysisWeights.JD_ABSENT.dimensions()).containsExactly(
                PROBLEM_SOLVING, PROJECT_EXPERIENCE, TECHNICAL_SKILLS, SOFT_SKILLS);
    }

    @Test
    void 지표_키는_toolKey가_단일_소스다() {
        assertThat(ResumeAnalysisWeights.JD_PROVIDED.dimensions().stream()
                .map(ResumeAnalysisDimension::toolKey)
                .toList())
                .containsExactly("problem_solving", "project_experience", "technical_skills", "soft_skills", "jd_fit");
    }

    @Test
    void 프롬프트의_가중치_문자열은_코드의_가중치와_일치한다() {
        assertWeightLines(ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITH_JD, ResumeAnalysisWeights.JD_PROVIDED);
        assertWeightLines(ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITHOUT_JD, ResumeAnalysisWeights.JD_ABSENT);
        assertThat(ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITHOUT_JD)
                .doesNotContain("- " + ResumeAnalysisDimension.JD_FIT.toolKey());
    }

    private void assertWeightLines(String prompt, ResumeAnalysisWeights weights) {
        for (ResumeAnalysisDimension dimension : weights.dimensions()) {
            assertThat(prompt)
                    .as("%s 가중치 줄이 프롬프트와 코드에서 어긋났다", dimension.toolKey())
                    .contains("- %s %s".formatted(dimension.toolKey(),
                            String.format(Locale.ROOT, "%.2f", weights.weightOf(dimension))));
        }
        assertThat(prompt.lines().filter(line -> line.startsWith("- ")).count())
                .as("프롬프트의 가중치 줄 개수가 가중치 세트의 차원 수와 다르다")
                .isEqualTo(weights.dimensions().size());
    }

    private static double sumOfWeights(ResumeAnalysisWeights weights) {
        return weights.dimensions().stream()
                .mapToDouble(dimension -> weights.weightOf(dimension))
                .sum();
    }

    private static ResumeAnalysisEvaluation evaluationWithJdFit(int problemSolving, int projectExperience,
                                                               int technicalSkills, int softSkills, int jdFit) {
        return new ResumeAnalysisEvaluation(dimensionScore(problemSolving), dimensionScore(projectExperience),
                dimensionScore(technicalSkills), dimensionScore(softSkills), dimensionScore(jdFit), null, "종합 총평");
    }

    private static ResumeAnalysisEvaluation evaluationWithoutJdFit(int problemSolving, int projectExperience,
                                                                  int technicalSkills, int softSkills) {
        return new ResumeAnalysisEvaluation(dimensionScore(problemSolving), dimensionScore(projectExperience),
                dimensionScore(technicalSkills), dimensionScore(softSkills), null, null, "종합 총평");
    }

    private static DimensionScore dimensionScore(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }
}
