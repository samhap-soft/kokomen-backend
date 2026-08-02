package com.samhap.kokomen.resume.service.dto;

import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.JD_FIT;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROBLEM_SOLVING;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.PROJECT_EXPERIENCE;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.SOFT_SKILLS;
import static com.samhap.kokomen.resume.domain.ResumeAnalysisDimension.TECHNICAL_SKILLS;

import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;

public record ResumeAnalysisEvaluationResponse(
        ResumeAnalysisDimensionResponse problemSolving,
        ResumeAnalysisDimensionResponse projectExperience,
        ResumeAnalysisDimensionResponse technicalSkills,
        ResumeAnalysisDimensionResponse softSkills,
        ResumeAnalysisDimensionResponse jdFit,
        Integer totalScore,
        String totalFeedback
) {

    public static ResumeAnalysisEvaluationResponse fromNullable(ResumeAnalysis analysis) {
        if (!analysis.getState().isEvaluationRevealed()) {
            return null;
        }
        ResumeAnalysisWeights weights = ResumeAnalysisWeights.of(analysis.isJdProvided());
        return new ResumeAnalysisEvaluationResponse(
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getProblemSolvingScore(),
                        weights.weightOf(PROBLEM_SOLVING), analysis.getProblemSolvingReason(),
                        analysis.getProblemSolvingImprovements()),
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getProjectExperienceScore(),
                        weights.weightOf(PROJECT_EXPERIENCE), analysis.getProjectExperienceReason(),
                        analysis.getProjectExperienceImprovements()),
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getTechnicalSkillsScore(),
                        weights.weightOf(TECHNICAL_SKILLS), analysis.getTechnicalSkillsReason(),
                        analysis.getTechnicalSkillsImprovements()),
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getSoftSkillsScore(),
                        weights.weightOf(SOFT_SKILLS), analysis.getSoftSkillsReason(),
                        analysis.getSoftSkillsImprovements()),
                ResumeAnalysisDimensionResponse.fromNullable(analysis.getJdFitScore(),
                        weights.weightOf(JD_FIT), analysis.getJdFitReason(),
                        analysis.getJdFitImprovements()),
                analysis.getTotalScore(),
                analysis.getTotalFeedback());
    }
}
