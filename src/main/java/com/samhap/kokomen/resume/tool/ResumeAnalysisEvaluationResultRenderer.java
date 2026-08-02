package com.samhap.kokomen.resume.tool;

import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 질문 콜 user 메시지의 {@code <evaluation_result>} 본문을 렌더한다.
 * 주입 대상은 차원별 score 전량 + improvements 전량 + reason 앞 2개 + total_score + jd_provided이며,
 * {@code {dim}_reasoning}과 {@code total_feedback}은 순증 정보가 없어 주입하지 않는다.
 * 차원 순서는 {@code ResumeAnalysisWeights.dimensions()}(= 지표 enum 선언 순서)를 따른다.
 * {@code reason}이 빈 리스트면 {@code strengths: (없음)}으로 렌더한다(빈 줄은 모델이 필드 누락으로 오독한다).
 */
public final class ResumeAnalysisEvaluationResultRenderer {

    private static final String HEADER =
            "이 결과는 같은 이력서·포트폴리오를 대상으로 방금 수행된 평가다. 점수가 낮은 차원과 gaps(검증 공백)를 질문 표적 선정에 사용한다. "
                    + "strengths는 각 차원의 대표 근거 2개만 발췌한 것이다.";
    private static final String EMPTY_MARK = "(없음)";
    private static final String BULLET_DELIMITER = " | ";
    private static final int STRENGTH_LIMIT = 2;

    private ResumeAnalysisEvaluationResultRenderer() {
    }

    public static String render(ResumeAnalysisEvaluation evaluation, boolean jdProvided) {
        StringBuilder rendered = new StringBuilder(HEADER).append('\n');
        for (ResumeAnalysisDimension dimension : ResumeAnalysisWeights.of(jdProvided).dimensions()) {
            DimensionScore dimensionScore = dimensionScoreOf(evaluation, dimension);
            if (dimensionScore == null) {
                continue;
            }
            rendered.append(renderDimension(dimension, dimensionScore));
        }
        return rendered.append("overall: total_score=%d, jd_provided=%b"
                        .formatted(evaluation.totalScore(), jdProvided))
                .toString();
    }

    private static DimensionScore dimensionScoreOf(ResumeAnalysisEvaluation evaluation,
                                                   ResumeAnalysisDimension dimension) {
        return switch (dimension) {
            case PROBLEM_SOLVING -> evaluation.problemSolving();
            case PROJECT_EXPERIENCE -> evaluation.projectExperience();
            case TECHNICAL_SKILLS -> evaluation.technicalSkills();
            case SOFT_SKILLS -> evaluation.softSkills();
            case JD_FIT -> evaluation.jdFit();
        };
    }

    private static String renderDimension(ResumeAnalysisDimension dimension, DimensionScore dimensionScore) {
        return """
                <dimension name="%s" score="%d">
                strengths: %s
                gaps: %s
                </dimension>
                """.formatted(
                dimension.toolKey(),
                dimensionScore.score(),
                joinBullets(dimensionScore.reason(), STRENGTH_LIMIT),
                joinBullets(dimensionScore.improvements(), Integer.MAX_VALUE));
    }

    private static String joinBullets(List<String> bullets, int limit) {
        if (bullets == null || bullets.isEmpty()) {
            return EMPTY_MARK;
        }
        return bullets.stream()
                .limit(limit)
                .map(ResumeAnalysisEvaluationResultRenderer::sanitize)
                .collect(Collectors.joining(BULLET_DELIMITER));
    }

    /**
     * 구분자 {@code |}는 {@code /}로, 태그 괄호 {@code <}·{@code >}는 각각 {@code (}·{@code )}로 치환해
     * 렌더 결과의 파싱 혼동을 막는다. 여는 괄호만 치환하면 {@code (job_requirements>}처럼 짝이 맞지 않는
     * 문자열이 남아 모델이 태그 경계로 오독하므로 닫는 괄호도 함께 치환한다.
     */
    private static String sanitize(String bullet) {
        return bullet.replace("|", "/").replace("<", "(").replace(">", ")");
    }
}
