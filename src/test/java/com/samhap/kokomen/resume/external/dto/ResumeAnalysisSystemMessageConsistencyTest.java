package com.samhap.kokomen.resume.external.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.tool.ResumeAnalysisEvaluationResultRenderer;
import com.samhap.kokomen.resume.tool.ResumeAnalysisPromptFragments;
import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 이력서 분석(5지표) 프롬프트의 일관성을 검증한다.
 * 폐기된 구 지표명·구 관찰항목이 신규 프롬프트에 재유입되지 않는지도 함께 단정한다.
 */
class ResumeAnalysisSystemMessageConsistencyTest {

    @Test
    void 평가_시스템_메시지는_신규5지표_이름을_모두_포함한다() {
        String message = ResumeAnalysisSystemMessages.evaluation(true);

        for (ResumeAnalysisDimension dimension : ResumeAnalysisDimension.values()) {
            assertThat(message)
                    .as("%s 지표 키가 프롬프트에 없다", dimension.toolKey())
                    .contains(dimension.toolKey());
        }
    }

    @Test
    void 평가_시스템_메시지는_구지표_이름을_포함하지_않는다() {
        assertThat(ResumeAnalysisSystemMessages.evaluation(true))
                .doesNotContain("career_growth", "documentation");
        assertThat(ResumeAnalysisSystemMessages.evaluation(false))
                .doesNotContain("career_growth", "documentation");
    }

    @Test
    void JD가_있으면_평가_프롬프트에_JD적합성_지시가_들어간다() {
        String message = ResumeAnalysisSystemMessages.evaluation(true);

        assertThat(message).contains(
                ResumeAnalysisPromptFragments.DIMENSION_JD_FIT,
                ResumeAnalysisPromptFragments.ANCHOR_JD_FIT,
                ResumeAnalysisPromptFragments.JD_POLICY_PROVIDED,
                ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITH_JD);
        assertThat(message).contains("- jd_fit 0.15");
    }

    @Test
    void JD가_없으면_JD적합성_지시가_없고_4지표_가중치가_명시된다() {
        String message = ResumeAnalysisSystemMessages.evaluation(false);

        assertThat(message).doesNotContain(
                ResumeAnalysisPromptFragments.DIMENSION_JD_FIT,
                ResumeAnalysisPromptFragments.ANCHOR_JD_FIT,
                ResumeAnalysisPromptFragments.JD_POLICY_PROVIDED);
        assertThat(message).contains(
                ResumeAnalysisPromptFragments.JD_POLICY_ABSENT,
                ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITHOUT_JD);
        assertThat(message).contains("- problem_solving 0.30", "- soft_skills 0.10");
        assertThat(message).doesNotContain("- jd_fit 0.15", "- jd_fit 0.30");
    }

    @Test
    void JD_부재를_감점_사유로_삼지_말라는_규칙이_유지된다() {
        assertThat(ResumeAnalysisSystemMessages.evaluation(false))
                .contains("JD 부재 자체를 감점 사유로 삼거나");
    }

    @Test
    void 소프트스킬_기준은_근거_부재를_감점하지_않고_중립_기준점으로_채점한다고_명시한다() {
        String message = ResumeAnalysisSystemMessages.evaluation(false);

        assertThat(message).contains(
                ResumeAnalysisPromptFragments.SOFT_SKILLS_NEUTRAL_BASELINE);
        assertThat(message).contains(
                "중립 기준점",
                "부재를 감점 사유로 쓰지 않는다",
                "관찰 근거 없음 → 중립 기준점 적용");
    }

    @Test
    void 소프트스킬은_근거가_있을_때만_채점하는_항목을_명시한다() {
        // 멘토링·조직 개편 관찰항목은 삭제하지 않고, 관찰 근거가 있을 때만 채점하도록 남겨 둔다.
        assertThat(ResumeAnalysisSystemMessages.evaluation(false)).contains(
                "STAR",
                "본인이 담당한 역할",
                "기술 블로그",
                "멘토링",
                "조직 개편",
                "갈등 해결",
                "기재되어 있을 때에만 채점");
    }

    @Test
    void 폐기된_구_관찰항목은_신규_프롬프트에_없다() {
        assertThat(ResumeAnalysisSystemMessages.evaluation(true))
                .doesNotContain("오탈자", "경력 발전 경로", "지속적인 학습");
        assertThat(ResumeAnalysisSystemMessages.evaluation(false))
                .doesNotContain("오탈자", "경력 발전 경로", "지속적인 학습");
    }

    @Test
    void 독립성_원칙과_보안규칙은_신규_평가_프롬프트에도_포함된다() {
        assertThat(ResumeAnalysisSystemMessages.evaluation(true)).contains(
                ResumeAnalysisPromptFragments.SECURITY_RULES,
                ResumeAnalysisPromptFragments.SENIOR_INTERVIEWER_LENS,
                ResumeAnalysisPromptFragments.INDEPENDENCE_PRINCIPLE,
                ResumeAnalysisPromptFragments.EVALUATION_INSTRUCTION,
                ResumeAnalysisPromptFragments.IMPROVEMENT_RULES,
                ResumeAnalysisPromptFragments.IMPROVEMENT_EXAMPLES);
    }

    @Test
    void 신규_페르소나_인칭도_너로_통일됐다() {
        assertThat(ResumeAnalysisSystemMessages.evaluation(true)).startsWith("<role>\n너는");
        assertThat(ResumeAnalysisSystemMessages.questionGeneration()).startsWith("<role>\n너는");
    }

    @Test
    void 질문_시스템_메시지는_질문가이드와_probe렌즈와_평가결과_근거규칙을_포함한다() {
        assertThat(ResumeAnalysisSystemMessages.questionGeneration()).contains(
                ResumeAnalysisPromptFragments.PERSONA_INTERVIEWER,
                ResumeAnalysisPromptFragments.QUESTION_GENERATION_GUIDE,
                ResumeAnalysisPromptFragments.QUESTION_PROBE_LENS,
                ResumeAnalysisPromptFragments.EVALUATION_GROUNDING_RULE);
    }

    @Test
    void 신규_질문_가이드는_평가결과_활용_항목을_포함한다() {
        assertThat(ResumeAnalysisPromptFragments.QUESTION_GENERATION_GUIDE).contains(
                "8. <evaluation_result>가 제공된 경우 질문 배분의 우선순위 근거로 사용하며, "
                        + "<evaluation_grounding_rule>을 준수한다.");
    }

    @Test
    void 질문_시스템_메시지는_평가결과와_무관하게_항상_동일하다() {
        // questionGeneration()이 무인자인 것이 캐시 프리픽스 불변의 컴파일 타임 보장이다.
        String first = ResumeAnalysisSystemMessages.questionGeneration();
        String second = ResumeAnalysisSystemMessages.questionGeneration();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 평가결과_렌더러는_JD가_있으면_다섯_차원을_렌더한다() {
        String rendered = ResumeAnalysisEvaluationResultRenderer.render(
                evaluation(new DimensionScore(64, List.of("도메인 경험 일치"), List.of("우대 사항 키워드 보강"))), true);

        assertThat(rendered).contains(
                "<dimension name=\"problem_solving\" score=\"62\">",
                "<dimension name=\"project_experience\" score=\"78\">",
                "<dimension name=\"technical_skills\" score=\"71\">",
                "<dimension name=\"soft_skills\" score=\"55\">",
                "<dimension name=\"jd_fit\" score=\"64\">");
        assertThat(rendered).endsWith("overall: total_score=68, jd_provided=true");
        assertThat(rendered).startsWith("이 결과는 같은 이력서·포트폴리오를 대상으로 방금 수행된 평가다.");
    }

    @Test
    void 평가결과_렌더러는_JD가_없으면_jd_fit_블록을_생략한다() {
        String rendered = ResumeAnalysisEvaluationResultRenderer.render(evaluation(null), false);

        assertThat(rendered).doesNotContain("jd_fit\"");
        assertThat(rendered).contains("<dimension name=\"soft_skills\" score=\"55\">");
        assertThat(rendered).endsWith("overall: total_score=68, jd_provided=false");
    }

    @Test
    void 평가결과_렌더러는_대표_근거_두개만_발췌한다() {
        String rendered = ResumeAnalysisEvaluationResultRenderer.render(evaluation(null), false);

        assertThat(rendered).contains("strengths: 문제 상황이 특정됨 | 지표로 검증함");
        assertThat(rendered).doesNotContain("세 번째 근거");
        assertThat(rendered).contains("gaps: 측정 방법을 덧붙여라 | 대안 배제 이유를 덧붙여라");
    }

    @Test
    void 평가결과_렌더러는_근거가_없으면_없음으로_표기한다() {
        // DimensionScore의 reason은 빈 리스트를 허용하고 improvements는 non-empty여야 한다.
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                new DimensionScore(62, List.of(), List.of("측정 방법을 덧붙여라")),
                new DimensionScore(78, List.of("역할이 구분됨"), List.of("사후 관리 경험을 덧붙여라")),
                new DimensionScore(71, List.of("주력 스택이 명확함"), List.of("GitHub 링크를 덧붙여라")),
                new DimensionScore(55, List.of(), List.of("협업 대상 직군을 덧붙여라")),
                null, 68, "종합 총평");

        String rendered = ResumeAnalysisEvaluationResultRenderer.render(evaluation, false);

        assertThat(rendered).contains("strengths: (없음)");
        assertThat(rendered).doesNotContain("strengths: \n");
        assertThat(rendered.lines()).noneMatch(String::isBlank);
    }

    @Test
    void 평가결과_렌더러는_구분자와_괄호를_치환한다() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                new DimensionScore(62, List.of("응답 지연 320ms | 180ms 개선", "<job_requirements> 대조 결과"),
                        List.of("측정 기준 | 시점을 덧붙여라")),
                new DimensionScore(78, List.of("역할이 구분됨"), List.of("사후 관리 경험을 덧붙여라")),
                new DimensionScore(71, List.of("주력 스택이 명확함"), List.of("GitHub 링크를 덧붙여라")),
                new DimensionScore(55, List.of("STAR 구조가 읽힘"), List.of("협업 대상 직군을 덧붙여라")),
                null, 68, "종합 총평");

        String rendered = ResumeAnalysisEvaluationResultRenderer.render(evaluation, false);

        assertThat(rendered).contains("strengths: 응답 지연 320ms / 180ms 개선 | (job_requirements) 대조 결과");
        assertThat(rendered).contains("gaps: 측정 기준 / 시점을 덧붙여라");
    }

    private ResumeAnalysisEvaluation evaluation(DimensionScore jdFit) {
        return new ResumeAnalysisEvaluation(
                new DimensionScore(62, List.of("문제 상황이 특정됨", "지표로 검증함", "세 번째 근거"),
                        List.of("측정 방법을 덧붙여라", "대안 배제 이유를 덧붙여라")),
                new DimensionScore(78, List.of("역할이 구분됨", "정량 성과가 있음"),
                        List.of("사후 관리 경험을 덧붙여라")),
                new DimensionScore(71, List.of("주력 스택이 명확함", "난제 해결 기록이 있음"),
                        List.of("GitHub 링크를 덧붙여라")),
                new DimensionScore(55, List.of("STAR 구조가 읽힘"),
                        List.of("협업 대상 직군을 덧붙여라")),
                jdFit, 68, "종합 총평");
    }
}
