package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.service.dto.evaluation.CareerGrowthResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.DocumentationResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.ProblemSolvingResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.ProjectExperienceResponse;
import com.samhap.kokomen.resume.service.dto.evaluation.TechnicalSkillsResponse;
import java.util.List;

public record ResumeEvaluationResponse(
        TechnicalSkillsResponse technicalSkills,
        ProjectExperienceResponse projectExperience,
        ProblemSolvingResponse problemSolving,
        CareerGrowthResponse careerGrowth,
        DocumentationResponse documentation,
        int totalScore,
        String totalFeedback
) {
    public ResumeEvaluationResponse withCalculatedTotalScore() {
        int calculated = (int) Math.round(
                0.30 * technicalSkills.score()
                        + 0.25 * projectExperience.score()
                        + 0.20 * problemSolving.score()
                        + 0.15 * careerGrowth.score()
                        + 0.10 * documentation.score()
        );
        return new ResumeEvaluationResponse(
                technicalSkills, projectExperience, problemSolving, careerGrowth, documentation,
                calculated, totalFeedback
        );
    }

    public static ResumeEvaluationResponse from(ResumeEvaluation evaluation) {
        return new ResumeEvaluationResponse(
                new TechnicalSkillsResponse(
                        nullToZero(evaluation.getTechnicalSkillsScore()),
                        nullToEmpty(evaluation.getTechnicalSkillsReason()),
                        nullToEmpty(evaluation.getTechnicalSkillsImprovements())
                ),
                new ProjectExperienceResponse(
                        nullToZero(evaluation.getProjectExperienceScore()),
                        nullToEmpty(evaluation.getProjectExperienceReason()),
                        nullToEmpty(evaluation.getProjectExperienceImprovements())
                ),
                new ProblemSolvingResponse(
                        nullToZero(evaluation.getProblemSolvingScore()),
                        nullToEmpty(evaluation.getProblemSolvingReason()),
                        nullToEmpty(evaluation.getProblemSolvingImprovements())
                ),
                new CareerGrowthResponse(
                        nullToZero(evaluation.getCareerGrowthScore()),
                        nullToEmpty(evaluation.getCareerGrowthReason()),
                        nullToEmpty(evaluation.getCareerGrowthImprovements())
                ),
                new DocumentationResponse(
                        nullToZero(evaluation.getDocumentationScore()),
                        nullToEmpty(evaluation.getDocumentationReason()),
                        nullToEmpty(evaluation.getDocumentationImprovements())
                ),
                nullToZero(evaluation.getTotalScore()),
                evaluation.getTotalFeedback()
        );
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private static List<String> nullToEmpty(List<String> value) {
        return value != null ? value : List.of();
    }
}
