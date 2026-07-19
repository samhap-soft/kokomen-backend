package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.external.dto.ResumeEvaluationLlmResponse.CategoryScore;
import java.util.List;

/**
 * 이력서 평가 tool 응답의 flat 와이어 DTO. Bedrock(tool-use)·GPT(function-calling) 모두 중첩 object를 피하려고
 * 5개 카테고리를 {category}_score/reason/improvements 형태의 flat 필드로 받는다
 * (중첩 object는 Claude가 &lt;parameter name=...&gt; XML을 흘려 파싱 실패를 유발하므로 flat 유지 — [[bedrock-tool-schema-no-nested-objects]]).
 * 스키마에 있는 {category}_reasoning(CoT)은 도메인에 쓰이지 않아 여기서 받지 않고 unknown-property로 무시한다.
 * totalScore는 서버에서 가중평균으로 재계산하므로 이 DTO는 받지 않는다.
 */
public record ResumeEvaluationFlatResponse(
        int technicalSkillsScore,
        List<String> technicalSkillsReason,
        List<String> technicalSkillsImprovements,
        int projectExperienceScore,
        List<String> projectExperienceReason,
        List<String> projectExperienceImprovements,
        int problemSolvingScore,
        List<String> problemSolvingReason,
        List<String> problemSolvingImprovements,
        int careerGrowthScore,
        List<String> careerGrowthReason,
        List<String> careerGrowthImprovements,
        int documentationScore,
        List<String> documentationReason,
        List<String> documentationImprovements,
        String totalFeedback
) {

    /**
     * flat 와이어 응답을 기존 중첩 도메인 모델로 변환한다. 종합 점수(가중평균)를 이 시점에 항상 계산해 반환하므로
     * 호출부가 별도로 withCalculatedTotalScore를 부를 필요가 없다(종합 점수 0 저장 같은 시간적 결합 방지).
     */
    public ResumeEvaluationLlmResponse toLlmResponse() {
        return new ResumeEvaluationLlmResponse(
                new CategoryScore(technicalSkillsScore, technicalSkillsReason, technicalSkillsImprovements),
                new CategoryScore(projectExperienceScore, projectExperienceReason, projectExperienceImprovements),
                new CategoryScore(problemSolvingScore, problemSolvingReason, problemSolvingImprovements),
                new CategoryScore(careerGrowthScore, careerGrowthReason, careerGrowthImprovements),
                new CategoryScore(documentationScore, documentationReason, documentationImprovements),
                0,
                totalFeedback
        ).withCalculatedTotalScore();
    }
}
