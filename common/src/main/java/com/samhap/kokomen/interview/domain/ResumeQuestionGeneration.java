package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
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
@Table(name = "resume_question_generation", indexes = {
        @Index(name = "idx_resume_question_generation_member_id", columnList = "member_id")
})
public class ResumeQuestionGeneration extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_resume_id")
    private MemberResume memberResume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_portfolio_id")
    private MemberPortfolio memberPortfolio;

    @Column(name = "job_career", length = 100)
    private String jobCareer;

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    private ResumeQuestionGenerationState state;

    public ResumeQuestionGeneration(
            Member member,
            MemberResume memberResume,
            MemberPortfolio memberPortfolio,
            String jobCareer
    ) {
        this.member = member;
        this.memberResume = memberResume;
        this.memberPortfolio = memberPortfolio;
        this.jobCareer = jobCareer;
        this.state = ResumeQuestionGenerationState.PENDING;
    }

    public boolean isOwner(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    public boolean isCompleted() {
        return this.state == ResumeQuestionGenerationState.COMPLETED;
    }

    public void complete() {
        this.state = ResumeQuestionGenerationState.COMPLETED;
    }

    public void fail() {
        this.state = ResumeQuestionGenerationState.FAILED;
    }
}
