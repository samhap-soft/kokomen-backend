package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.List;

/**
 * 이력서 분석 평가 tool 응답의 flat 와이어 DTO. 중첩 object는 Claude가 &lt;parameter name=...&gt; XML을 흘려
 * 파싱 실패를 유발하므로 차원별 필드를 flat으로 받는다.
 * {dim}_reasoning 5필드는 선언하지 않는다(FAIL_ON_UNKNOWN_PROPERTIES=false 전제로 무시).
 */
public record ResumeAnalysisEvaluationFlatResponse(
        Integer problemSolvingScore,
        List<String> problemSolvingReason,
        List<String> problemSolvingImprovements,
        Integer projectExperienceScore,
        List<String> projectExperienceReason,
        List<String> projectExperienceImprovements,
        Integer technicalSkillsScore,
        List<String> technicalSkillsReason,
        List<String> technicalSkillsImprovements,
        Integer softSkillsScore,
        List<String> softSkillsReason,
        List<String> softSkillsImprovements,
        Integer jdFitScore,
        List<String> jdFitReason,
        List<String> jdFitImprovements,
        String totalFeedback
) {

    /**
     * jdProvided를 인자로 받는 이유: 응답 JSON만으로는 "jd_fit이 없음"과 "jd_fit이 누락됨"을
     * 구분할 수 없으므로 요청 측 사실을 전달해야 한다.
     */
    public ResumeAnalysisEvaluation toEvaluation(boolean jdProvided) {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                new DimensionScore(problemSolvingScore, problemSolvingReason, problemSolvingImprovements),
                new DimensionScore(projectExperienceScore, projectExperienceReason, projectExperienceImprovements),
                new DimensionScore(technicalSkillsScore, technicalSkillsReason, technicalSkillsImprovements),
                new DimensionScore(softSkillsScore, softSkillsReason, softSkillsImprovements),
                jdProvided ? new DimensionScore(jdFitScore, jdFitReason, jdFitImprovements) : null,
                null, totalFeedback);
        return evaluation.withTotalScore(
                ResumeAnalysisWeights.of(jdProvided).calculateTotalScore(evaluation));
    }
}
