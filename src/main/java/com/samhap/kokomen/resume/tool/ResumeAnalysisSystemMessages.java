package com.samhap.kokomen.resume.tool;

import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 이력서 분석(5지표) 시스템 메시지의 GPT·Bedrock 공용 단일 소스.
 * {@code questionGeneration()}은 의도적으로 무인자다: 평가 결과는 user 메시지에만 주입하며,
 * system을 요청별로 바꾸면 Bedrock 캐시 프리픽스가 요청마다 갈려 캐시가 전면 무효화된다.
 */
public final class ResumeAnalysisSystemMessages {

    private ResumeAnalysisSystemMessages() {
    }

    public static String evaluation(boolean jdProvided) {
        List<String> fragments = new ArrayList<>();
        fragments.add(ResumeAnalysisPromptFragments.SECURITY_RULES);
        fragments.add(ResumeAnalysisPromptFragments.SENIOR_INTERVIEWER_LENS);
        fragments.add(evaluationCriteria(jdProvided));
        fragments.add(jdProvided
                ? ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITH_JD
                : ResumeAnalysisPromptFragments.SCORING_WEIGHTS_WITHOUT_JD);
        fragments.add(ResumeAnalysisPromptFragments.EVALUATION_INSTRUCTION);
        fragments.add(ResumeAnalysisPromptFragments.IMPROVEMENT_RULES);
        fragments.add(ResumeAnalysisPromptFragments.IMPROVEMENT_EXAMPLES);
        fragments.add(ResumeAnalysisPromptFragments.SOFT_SKILLS_NEUTRAL_BASELINE);
        fragments.add(jdProvided
                ? ResumeAnalysisPromptFragments.JD_POLICY_PROVIDED
                : ResumeAnalysisPromptFragments.JD_POLICY_ABSENT);
        fragments.add(ResumeAnalysisPromptFragments.INDEPENDENCE_PRINCIPLE);
        fragments.add(scoreAnchors(jdProvided));

        List<String> dimensionKeys = dimensionKeys(jdProvided);
        return """
                <role>
                %s
                </role>

                <task>
                10년차 시니어 면접관의 시선으로, 지원 직무와 (제공된 경우) 채용 공고를 기준 삼아 이력서와 포트폴리오를 검증하듯 종합 분석하여 차원별 객관적 평가와 점수를 산출하고, 지원자가 이력서에서 곧바로 실행할 수 있는 구체적 보완점을 도출하라.
                </task>

                %s

                <output>
                제공된 도구를 호출하여 다음 필드를 모두 제출하라.
                - %d개 차원(%s) 각각에 대해 {차원}_reasoning(점수 산정 전 사고 과정), {차원}_score(0-100, score_anchors 기준), {차원}_reason(평가 이유 배열, 2-6개), {차원}_improvements(보완 사항 배열, 2-6개)를 제출한다(예: problem_solving_score, problem_solving_reason).
                - total_feedback : 강점·개선·학습 방향을 포함한 종합 총평(한 단락). improvements 중 지원자가 가장 먼저 고쳐야 할 1~2개를 우선순위로 지목한다.
                - 도구 입력 스키마에 없는 필드는 절대 만들어 내지 않는다. (종합 점수는 서버에서 가중평균으로 재계산하므로 별도 출력하지 않는다.)
                </output>
                """.formatted(
                ResumeAnalysisPromptFragments.PERSONA_RECRUITER,
                joinFragments(fragments),
                dimensionKeys.size(),
                String.join(", ", dimensionKeys));
    }

    public static String questionGeneration() {
        return """
                <role>
                %s
                </role>

                <task>
                제공된 이력서, 포트폴리오, 직무 경력 정보와 <evaluation_result>(같은 문서에 대해 방금 수행된 평가 결과)를 함께 분석하여, 기술 면접에서 물어볼 핵심 질문들을 생성하라.
                </task>

                %s

                %s

                %s

                <output>
                제공된 도구를 호출하여 questions 배열을 제출하라. 각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
                </output>
                """.formatted(
                ResumeAnalysisPromptFragments.PERSONA_INTERVIEWER,
                ResumeAnalysisPromptFragments.QUESTION_GENERATION_GUIDE,
                ResumeAnalysisPromptFragments.QUESTION_PROBE_LENS,
                ResumeAnalysisPromptFragments.EVALUATION_GROUNDING_RULE);
    }

    private static String evaluationCriteria(boolean jdProvided) {
        return """
                <evaluation_criteria>
                %s
                %s%s</evaluation_criteria>
                """.formatted(
                ResumeAnalysisPromptFragments.CRITERIA_INTRO,
                ResumeAnalysisPromptFragments.DIMENSIONS_BASE,
                jdProvided ? ResumeAnalysisPromptFragments.DIMENSION_JD_FIT : "");
    }

    private static String scoreAnchors(boolean jdProvided) {
        return """
                <score_anchors>
                %s
                %s%s</score_anchors>
                """.formatted(
                ResumeAnalysisPromptFragments.ANCHORS_INTRO,
                ResumeAnalysisPromptFragments.ANCHORS_BASE,
                jdProvided ? ResumeAnalysisPromptFragments.ANCHOR_JD_FIT : "");
    }

    /**
     * 지표 키의 단일 소스는 {@code ResumeAnalysisDimension.toolKey()}이고, 차원 목록의 단일 소스는
     * {@code ResumeAnalysisWeights}다. {@code ResumeAnalysisSchema.dimensionKeys(boolean)}도 같은 두 소스에서
     * 파생되므로, 이 클래스가 {@code ResumeAnalysisSchema}를 참조하지 않아도 스키마 필드 집합과 이 프롬프트
     * 문구가 어긋날 수 없다. 그 일치는 {@code ResumeAnalysisSystemMessageConsistencyTest}가 고정한다.
     */
    private static List<String> dimensionKeys(boolean jdProvided) {
        return ResumeAnalysisWeights.of(jdProvided).dimensions().stream()
                .map(ResumeAnalysisDimension::toolKey)
                .toList();
    }

    private static String joinFragments(List<String> fragments) {
        return fragments.stream()
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
