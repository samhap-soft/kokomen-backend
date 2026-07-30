package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.global.exception.ExternalApiException;
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
     * 도구 스키마의 required는 모델에 대한 지시일 뿐 서버가 강제하는 계약이 아니므로, 구조적으로 유효한 JSON이
     * 여전히 _score 필드를 누락할 수 있다. DimensionScore.score는 primitive int라 그 경우 언박싱 NPE가
     * 발생하므로 여기서 잡아 ExternalApiException으로 통일한다 — Bedrock·GPT 두 provider 모두 이 메서드를
     * 통해서만 응답을 해석하므로, 한 곳에서 잡으면 두 provider가 동일하게 행동한다.
     */
    public ResumeAnalysisEvaluation toEvaluation(boolean jdProvided) {
        try {
            ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                    new DimensionScore(problemSolvingScore, problemSolvingReason, problemSolvingImprovements),
                    new DimensionScore(
                            projectExperienceScore, projectExperienceReason, projectExperienceImprovements),
                    new DimensionScore(technicalSkillsScore, technicalSkillsReason, technicalSkillsImprovements),
                    new DimensionScore(softSkillsScore, softSkillsReason, softSkillsImprovements),
                    jdProvided ? new DimensionScore(jdFitScore, jdFitReason, jdFitImprovements) : null,
                    null, totalFeedback);
            return evaluation.withTotalScore(
                    ResumeAnalysisWeights.of(jdProvided).calculateTotalScore(evaluation));
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(
                    "이력서 분석 평가 응답 파싱에 실패했습니다. jdProvided=" + jdProvided, e);
        }
    }
}
