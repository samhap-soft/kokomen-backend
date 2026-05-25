package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeEvaluationLlmResponse;
import com.samhap.kokomen.resume.external.dto.ResumeEvaluationLlmResponse.CategoryScore;
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
    public static ResumeEvaluationResponse from(ResumeEvaluationLlmResponse llm) {
        return new ResumeEvaluationResponse(
                toCategoryResponse(llm.technicalSkills(), TechnicalSkillsResponse::new),
                toCategoryResponse(llm.projectExperience(), ProjectExperienceResponse::new),
                toCategoryResponse(llm.problemSolving(), ProblemSolvingResponse::new),
                toCategoryResponse(llm.careerGrowth(), CareerGrowthResponse::new),
                toCategoryResponse(llm.documentation(), DocumentationResponse::new),
                llm.totalScore(),
                llm.totalFeedback()
        );
    }

    public static ResumeEvaluationResponse from(ResumeEvaluation evaluation) {
        return new ResumeEvaluationResponse(
                new TechnicalSkillsResponse(
                        nullToZero(evaluation.getTechnicalSkillsScore()),
                        joinWithNewline(evaluation.getTechnicalSkillsReason()),
                        joinWithNewline(evaluation.getTechnicalSkillsImprovements())
                ),
                new ProjectExperienceResponse(
                        nullToZero(evaluation.getProjectExperienceScore()),
                        joinWithNewline(evaluation.getProjectExperienceReason()),
                        joinWithNewline(evaluation.getProjectExperienceImprovements())
                ),
                new ProblemSolvingResponse(
                        nullToZero(evaluation.getProblemSolvingScore()),
                        joinWithNewline(evaluation.getProblemSolvingReason()),
                        joinWithNewline(evaluation.getProblemSolvingImprovements())
                ),
                new CareerGrowthResponse(
                        nullToZero(evaluation.getCareerGrowthScore()),
                        joinWithNewline(evaluation.getCareerGrowthReason()),
                        joinWithNewline(evaluation.getCareerGrowthImprovements())
                ),
                new DocumentationResponse(
                        nullToZero(evaluation.getDocumentationScore()),
                        joinWithNewline(evaluation.getDocumentationReason()),
                        joinWithNewline(evaluation.getDocumentationImprovements())
                ),
                nullToZero(evaluation.getTotalScore()),
                evaluation.getTotalFeedback()
        );
    }

    private static <T> T toCategoryResponse(CategoryScore category, CategoryResponseFactory<T> factory) {
        if (category == null) {
            return factory.create(0, "", "");
        }
        return factory.create(
                category.score(),
                joinWithNewline(category.reason()),
                joinWithNewline(category.improvements())
        );
    }

    @FunctionalInterface
    private interface CategoryResponseFactory<T> {
        T create(int score, String reason, String improvements);
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private static String joinWithNewline(List<String> value) {
        return value == null || value.isEmpty() ? "" : String.join("\n", value);
    }
}
