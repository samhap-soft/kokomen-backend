package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.service.dto.evaluation.CareerGrowthResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.DocumentationResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.ProblemSolvingResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.ProjectExperienceResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.TechnicalSkillsResponse;

public record ResumeEvaluationResponse(
        TechnicalSkillsResponse technicalSkills,
        ProjectExperienceResponse projectExperience,
        ProblemSolvingResponse problemSolving,
        CareerGrowthResponse careerGrowth,
        DocumentationResponse documentation,
        int totalScore,
        String totalFeedback
) {
    public static ResumeEvaluationResponse from(ResumeEvaluation evaluation) {
        return new ResumeEvaluationResponse(
                new TechnicalSkillsResponse(
                        nullToZero(evaluation.getTechnicalSkillsScore()),
                        evaluation.getTechnicalSkillsReason(),
                        evaluation.getTechnicalSkillsImprovements()
                ),
                new ProjectExperienceResponse(
                        nullToZero(evaluation.getProjectExperienceScore()),
                        evaluation.getProjectExperienceReason(),
                        evaluation.getProjectExperienceImprovements()
                ),
                new ProblemSolvingResponse(
                        nullToZero(evaluation.getProblemSolvingScore()),
                        evaluation.getProblemSolvingReason(),
                        evaluation.getProblemSolvingImprovements()
                ),
                new CareerGrowthResponse(
                        nullToZero(evaluation.getCareerGrowthScore()),
                        evaluation.getCareerGrowthReason(),
                        evaluation.getCareerGrowthImprovements()
                ),
                new DocumentationResponse(
                        nullToZero(evaluation.getDocumentationScore()),
                        evaluation.getDocumentationReason(),
                        evaluation.getDocumentationImprovements()
                ),
                nullToZero(evaluation.getTotalScore()),
                evaluation.getTotalFeedback()
        );
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
