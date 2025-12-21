package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "resume_evaluation", indexes = {
        @Index(name = "idx_resume_evaluation_member_id", columnList = "member_id")
})
public class ResumeEvaluation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ResumeEvaluationState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_resume_id")
    private MemberResume memberResume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_portfolio_id")
    private MemberPortfolio memberPortfolio;

    @Column(name = "job_position", nullable = false, length = 500)
    private String jobPosition;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "job_career", nullable = false, length = 100)
    private String jobCareer;

    @Column(name = "technical_skills_score")
    private Integer technicalSkillsScore;

    @Column(name = "technical_skills_reason", columnDefinition = "TEXT")
    private String technicalSkillsReason;

    @Column(name = "technical_skills_improvements", columnDefinition = "TEXT")
    private String technicalSkillsImprovements;

    @Column(name = "project_experience_score")
    private Integer projectExperienceScore;

    @Column(name = "project_experience_reason", columnDefinition = "TEXT")
    private String projectExperienceReason;

    @Column(name = "project_experience_improvements", columnDefinition = "TEXT")
    private String projectExperienceImprovements;

    @Column(name = "problem_solving_score")
    private Integer problemSolvingScore;

    @Column(name = "problem_solving_reason", columnDefinition = "TEXT")
    private String problemSolvingReason;

    @Column(name = "problem_solving_improvements", columnDefinition = "TEXT")
    private String problemSolvingImprovements;

    @Column(name = "career_growth_score")
    private Integer careerGrowthScore;

    @Column(name = "career_growth_reason", columnDefinition = "TEXT")
    private String careerGrowthReason;

    @Column(name = "career_growth_improvements", columnDefinition = "TEXT")
    private String careerGrowthImprovements;

    @Column(name = "documentation_score")
    private Integer documentationScore;

    @Column(name = "documentation_reason", columnDefinition = "TEXT")
    private String documentationReason;

    @Column(name = "documentation_improvements", columnDefinition = "TEXT")
    private String documentationImprovements;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "total_feedback", columnDefinition = "TEXT")
    private String totalFeedback;

    public ResumeEvaluation(Member member, MemberResume memberResume, MemberPortfolio memberPortfolio,
                            String jobPosition, String jobDescription, String jobCareer) {
        this.member = member;
        this.state = ResumeEvaluationState.PENDING;
        this.memberResume = memberResume;
        this.memberPortfolio = memberPortfolio;
        this.jobPosition = jobPosition;
        this.jobDescription = jobDescription;
        this.jobCareer = jobCareer;
    }

    public void complete(int technicalSkillsScore, String technicalSkillsReason, String technicalSkillsImprovements,
                         int projectExperienceScore, String projectExperienceReason,
                         String projectExperienceImprovements,
                         int problemSolvingScore, String problemSolvingReason, String problemSolvingImprovements,
                         int careerGrowthScore, String careerGrowthReason, String careerGrowthImprovements,
                         int documentationScore, String documentationReason, String documentationImprovements,
                         int totalScore, String totalFeedback) {
        this.state = ResumeEvaluationState.COMPLETED;
        this.technicalSkillsScore = technicalSkillsScore;
        this.technicalSkillsReason = technicalSkillsReason;
        this.technicalSkillsImprovements = technicalSkillsImprovements;
        this.projectExperienceScore = projectExperienceScore;
        this.projectExperienceReason = projectExperienceReason;
        this.projectExperienceImprovements = projectExperienceImprovements;
        this.problemSolvingScore = problemSolvingScore;
        this.problemSolvingReason = problemSolvingReason;
        this.problemSolvingImprovements = problemSolvingImprovements;
        this.careerGrowthScore = careerGrowthScore;
        this.careerGrowthReason = careerGrowthReason;
        this.careerGrowthImprovements = careerGrowthImprovements;
        this.documentationScore = documentationScore;
        this.documentationReason = documentationReason;
        this.documentationImprovements = documentationImprovements;
        this.totalScore = totalScore;
        this.totalFeedback = totalFeedback;
    }

    public void fail() {
        this.state = ResumeEvaluationState.FAILED;
    }

    public boolean isCompleted() {
        return this.state == ResumeEvaluationState.COMPLETED;
    }

    public boolean isPending() {
        return this.state == ResumeEvaluationState.PENDING;
    }

    public boolean isOwner(Long memberId) {
        return this.member.isOwner(memberId);
    }

    public void updateMemberResume(MemberResume memberResume, MemberPortfolio memberPortfolio) {
        this.memberResume = memberResume;
        this.memberPortfolio = memberPortfolio;
    }
}
