package com.samhap.kokomen.resume.domain;

import java.util.EnumMap;
import java.util.Map;

public record ResumeAnalysisEvaluation(
        DimensionScore problemSolving,
        DimensionScore projectExperience,
        DimensionScore technicalSkills,
        DimensionScore softSkills,
        DimensionScore jdFit,
        Integer totalScore,
        String totalFeedback
) {

    /**
     * 산출된 차원만 엔트리로 담는다. jdFit이 null이면 4개 엔트리이며, 이 성질이
     * "JD 미산출"과 "0점"을 구분하는 근거다(null을 0으로 채우지 않는다).
     */
    public Map<ResumeAnalysisDimension, Integer> scores() {
        Map<ResumeAnalysisDimension, Integer> scores = new EnumMap<>(ResumeAnalysisDimension.class);
        putScore(scores, ResumeAnalysisDimension.PROBLEM_SOLVING, problemSolving);
        putScore(scores, ResumeAnalysisDimension.PROJECT_EXPERIENCE, projectExperience);
        putScore(scores, ResumeAnalysisDimension.TECHNICAL_SKILLS, technicalSkills);
        putScore(scores, ResumeAnalysisDimension.SOFT_SKILLS, softSkills);
        putScore(scores, ResumeAnalysisDimension.JD_FIT, jdFit);
        return scores;
    }

    private static void putScore(Map<ResumeAnalysisDimension, Integer> scores, ResumeAnalysisDimension dimension,
                                 DimensionScore dimensionScore) {
        if (dimensionScore != null) {
            scores.put(dimension, dimensionScore.score());
        }
    }

    public ResumeAnalysisEvaluation withTotalScore(int totalScore) {
        return new ResumeAnalysisEvaluation(problemSolving, projectExperience, technicalSkills, softSkills, jdFit,
                totalScore, totalFeedback);
    }
}
