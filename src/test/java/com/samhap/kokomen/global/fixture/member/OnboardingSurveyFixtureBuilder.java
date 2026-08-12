package com.samhap.kokomen.global.fixture.member;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.survey.CareerGoal;
import com.samhap.kokomen.member.domain.survey.InterviewExperience;
import com.samhap.kokomen.member.domain.survey.OnboardingSurvey;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.TargetCompanyType;
import com.samhap.kokomen.member.domain.survey.WeakPoint;
import java.util.List;

public class OnboardingSurveyFixtureBuilder {

    private Member member;
    private CareerGoal careerGoal;
    private List<PrepStage> prepStages;
    private List<Category> techTopics;
    private TargetCompanyType targetCompanyType;
    private InterviewExperience interviewExperience;
    private List<WeakPoint> weakPoints;
    private String goalDescription;

    public static OnboardingSurveyFixtureBuilder builder() {
        return new OnboardingSurveyFixtureBuilder();
    }

    public OnboardingSurveyFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public OnboardingSurveyFixtureBuilder careerGoal(CareerGoal careerGoal) {
        this.careerGoal = careerGoal;
        return this;
    }

    public OnboardingSurveyFixtureBuilder prepStages(List<PrepStage> prepStages) {
        this.prepStages = prepStages;
        return this;
    }

    public OnboardingSurveyFixtureBuilder techTopics(List<Category> techTopics) {
        this.techTopics = techTopics;
        return this;
    }

    public OnboardingSurveyFixtureBuilder targetCompanyType(TargetCompanyType targetCompanyType) {
        this.targetCompanyType = targetCompanyType;
        return this;
    }

    public OnboardingSurveyFixtureBuilder interviewExperience(InterviewExperience interviewExperience) {
        this.interviewExperience = interviewExperience;
        return this;
    }

    public OnboardingSurveyFixtureBuilder weakPoints(List<WeakPoint> weakPoints) {
        this.weakPoints = weakPoints;
        return this;
    }

    public OnboardingSurveyFixtureBuilder goalDescription(String goalDescription) {
        this.goalDescription = goalDescription;
        return this;
    }

    public OnboardingSurvey build() {
        return new OnboardingSurvey(
                member,
                careerGoal != null ? careerGoal : CareerGoal.BACKEND,
                prepStages != null ? prepStages : List.of(PrepStage.JOB_SEEKING),
                techTopics != null ? techTopics : List.of(Category.JAVA_SPRING),
                targetCompanyType != null ? targetCompanyType : TargetCompanyType.BIG_TECH,
                interviewExperience != null ? interviewExperience : InterviewExperience.NONE,
                weakPoints != null ? weakPoints : List.of(WeakPoint.CS),
                goalDescription
        );
    }
}
