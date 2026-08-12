package com.samhap.kokomen.member.service.dto;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.member.domain.survey.CareerGoal;
import com.samhap.kokomen.member.domain.survey.InterviewExperience;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.TargetCompanyType;
import com.samhap.kokomen.member.domain.survey.WeakPoint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hibernate.validator.constraints.Length;

public record OnboardingSurveySubmitRequest(
        @NotNull(message = "career_goal은 null일 수 없습니다.")
        CareerGoal careerGoal,

        @NotEmpty(message = "prep_stages는 최소 1개를 선택해야 합니다.")
        List<PrepStage> prepStages,

        @NotEmpty(message = "tech_topics는 최소 1개를 선택해야 합니다.")
        List<Category> techTopics,

        @NotNull(message = "target_company_type은 null일 수 없습니다.")
        TargetCompanyType targetCompanyType,

        @NotNull(message = "interview_experience는 null일 수 없습니다.")
        InterviewExperience interviewExperience,

        @NotEmpty(message = "weak_points는 최소 1개를 선택해야 합니다.")
        List<WeakPoint> weakPoints,

        @Length(max = 1000, message = "goal_description은 최대 1000자까지 입력할 수 있습니다.")
        String goalDescription
) {
}
