package com.samhap.kokomen.resume.external.dto;

import java.util.List;

public record ResumeEvaluationLlmResponse(
        CategoryScore technicalSkills,
        CategoryScore projectExperience,
        CategoryScore problemSolving,
        CategoryScore careerGrowth,
        CategoryScore documentation,
        int totalScore,
        String totalFeedback
) {

    public ResumeEvaluationLlmResponse withCalculatedTotalScore() {
        int calculated = (int) Math.round(
                0.30 * scoreOf(technicalSkills)
                        + 0.25 * scoreOf(projectExperience)
                        + 0.20 * scoreOf(problemSolving)
                        + 0.15 * scoreOf(careerGrowth)
                        + 0.10 * scoreOf(documentation)
        );
        return new ResumeEvaluationLlmResponse(
                technicalSkills, projectExperience, problemSolving, careerGrowth, documentation,
                calculated, totalFeedback
        );
    }

    private static int scoreOf(CategoryScore category) {
        return category != null ? category.score() : 0;
    }

    public record CategoryScore(
            int score,
            List<String> reason,
            List<String> improvements
    ) {
    }
}
