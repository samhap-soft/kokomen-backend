package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;

public class ResumeEvaluationFixtureBuilder {

    private Member member;
    private MemberResume resume;
    private MemberPortfolio portfolio;
    private String jobPosition;
    private String jobDescription;
    private String jobCareer;
    private boolean completed;
    private boolean failed;

    private int technicalSkillsScore = 80;
    private String technicalSkillsReason = "기술 역량이 우수합니다.";
    private String technicalSkillsImprovements = "최신 기술 트렌드를 더 익히면 좋겠습니다.";
    private int projectExperienceScore = 85;
    private String projectExperienceReason = "프로젝트 경험이 풍부합니다.";
    private String projectExperienceImprovements = "팀 프로젝트 경험을 더 늘리면 좋겠습니다.";
    private int problemSolvingScore = 75;
    private String problemSolvingReason = "문제 해결 능력이 좋습니다.";
    private String problemSolvingImprovements = "알고리즘 학습을 권장합니다.";
    private int careerGrowthScore = 80;
    private String careerGrowthReason = "성장 가능성이 높습니다.";
    private String careerGrowthImprovements = "목표 설정을 더 구체화하면 좋겠습니다.";
    private int documentationScore = 85;
    private String documentationReason = "이력서 작성이 잘 되어 있습니다.";
    private String documentationImprovements = "프로젝트 설명을 더 상세히 작성하면 좋겠습니다.";
    private int totalScore = 81;
    private String totalFeedback = "전반적으로 우수한 지원자입니다.";

    public static ResumeEvaluationFixtureBuilder builder() {
        return new ResumeEvaluationFixtureBuilder();
    }

    public ResumeEvaluationFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public ResumeEvaluationFixtureBuilder resume(MemberResume resume) {
        this.resume = resume;
        return this;
    }

    public ResumeEvaluationFixtureBuilder portfolio(MemberPortfolio portfolio) {
        this.portfolio = portfolio;
        return this;
    }

    public ResumeEvaluationFixtureBuilder jobPosition(String jobPosition) {
        this.jobPosition = jobPosition;
        return this;
    }

    public ResumeEvaluationFixtureBuilder jobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
        return this;
    }

    public ResumeEvaluationFixtureBuilder jobCareer(String jobCareer) {
        this.jobCareer = jobCareer;
        return this;
    }

    public ResumeEvaluationFixtureBuilder completed() {
        this.completed = true;
        return this;
    }

    public ResumeEvaluationFixtureBuilder failed() {
        this.failed = true;
        return this;
    }

    public ResumeEvaluationFixtureBuilder totalScore(int totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public ResumeEvaluation build() {
        ResumeEvaluation evaluation = new ResumeEvaluation(
                member,
                resume,
                portfolio,
                jobPosition != null ? jobPosition : "백엔드 개발자",
                jobDescription != null ? jobDescription : "Spring Boot 기반 백엔드 개발",
                jobCareer != null ? jobCareer : "신입"
        );

        if (completed) {
            evaluation.complete(
                    technicalSkillsScore, technicalSkillsReason, technicalSkillsImprovements,
                    projectExperienceScore, projectExperienceReason, projectExperienceImprovements,
                    problemSolvingScore, problemSolvingReason, problemSolvingImprovements,
                    careerGrowthScore, careerGrowthReason, careerGrowthImprovements,
                    documentationScore, documentationReason, documentationImprovements,
                    totalScore, totalFeedback
            );
        }

        if (failed) {
            evaluation.fail();
        }

        return evaluation;
    }
}
