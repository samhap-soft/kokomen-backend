package com.samhap.kokomen.resume.service.dto;

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
}
