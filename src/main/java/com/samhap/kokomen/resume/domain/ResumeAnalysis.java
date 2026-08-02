package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.persistence.StringListJsonConverter;
import com.samhap.kokomen.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 엔티티 클래스명(= 엔티티명)의 스네이크 변환 결과가 {@code @Table(name)}과 반드시 일치해야 한다.
 * H2AutoIncrementCleaner가 docs 프로파일 @BeforeEach마다
 * ALTER TABLE resume_analysis ALTER COLUMN ID RESTART WITH 1 을 실행하므로 id 컬럼도 필수다.
 */
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "resume_analysis",
        indexes = {
                @Index(name = "idx_resume_analysis_member_id_created_at", columnList = "member_id, created_at"),
                @Index(name = "idx_resume_analysis_state_created_at", columnList = "state, created_at"),
                @Index(name = "idx_resume_analysis_state_question_started_at",
                        columnList = "state, question_started_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_resume_analysis_guest_token", columnNames = "guest_token")
        })
public class ResumeAnalysis extends BaseEntity {

    public static final int MAX_QUESTION_RETRY = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "guest_token", length = 36)
    private String guestToken;

    @Column(name = "guest_ip", length = 45)
    private String guestIp;

    @Column(name = "guest_lock_value", length = 36)
    private String guestLockValue;

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

    @Column(name = "jd_provided", nullable = false)
    private boolean jdProvided;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 30)
    private ResumeAnalysisState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private ResumeAnalysisFailureReason failureReason;

    @Column(name = "problem_solving_score")
    private Integer problemSolvingScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "problem_solving_reason", columnDefinition = "JSON")
    private List<String> problemSolvingReason;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "problem_solving_improvements", columnDefinition = "JSON")
    private List<String> problemSolvingImprovements;

    @Column(name = "project_experience_score")
    private Integer projectExperienceScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "project_experience_reason", columnDefinition = "JSON")
    private List<String> projectExperienceReason;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "project_experience_improvements", columnDefinition = "JSON")
    private List<String> projectExperienceImprovements;

    @Column(name = "technical_skills_score")
    private Integer technicalSkillsScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "technical_skills_reason", columnDefinition = "JSON")
    private List<String> technicalSkillsReason;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "technical_skills_improvements", columnDefinition = "JSON")
    private List<String> technicalSkillsImprovements;

    @Column(name = "soft_skills_score")
    private Integer softSkillsScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "soft_skills_reason", columnDefinition = "JSON")
    private List<String> softSkillsReason;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "soft_skills_improvements", columnDefinition = "JSON")
    private List<String> softSkillsImprovements;

    @Column(name = "jd_fit_score")
    private Integer jdFitScore;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "jd_fit_reason", columnDefinition = "JSON")
    private List<String> jdFitReason;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "jd_fit_improvements", columnDefinition = "JSON")
    private List<String> jdFitImprovements;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "total_feedback", columnDefinition = "TEXT")
    private String totalFeedback;

    @Column(name = "billing_required", nullable = false)
    private boolean billingRequired;

    @Column(name = "charged_token_count", nullable = false)
    private Integer chargedTokenCount;

    @Column(name = "token_charge_failed", nullable = false)
    private boolean tokenChargeFailed;

    @Column(name = "question_retry_count", nullable = false)
    private Integer questionRetryCount;

    @Column(name = "evaluation_completed_at")
    private LocalDateTime evaluationCompletedAt;

    @Column(name = "question_started_at")
    private LocalDateTime questionStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private ResumeAnalysis(Member member, MemberResume memberResume, MemberPortfolio memberPortfolio,
                           ResumeAnalysisJobInput jobInput, boolean billingRequired) {
        this.member = member;
        this.memberResume = memberResume;
        this.memberPortfolio = memberPortfolio;
        this.billingRequired = billingRequired;
        applyJobInput(jobInput);
        initializeProgress();
    }

    private ResumeAnalysis(String guestToken, ClientIp clientIp, String guestLockValue,
                           ResumeAnalysisJobInput jobInput) {
        this.guestToken = guestToken;
        this.guestIp = clientIp.address();
        this.guestLockValue = guestLockValue;
        this.billingRequired = false;
        applyJobInput(jobInput);
        initializeProgress();
    }

    public static ResumeAnalysis forMember(Member member, MemberResume memberResume,
                                           MemberPortfolio memberPortfolio, ResumeAnalysisJobInput jobInput,
                                           boolean billingRequired) {
        return new ResumeAnalysis(member, memberResume, memberPortfolio, jobInput, billingRequired);
    }

    public static ResumeAnalysis forGuest(String guestToken, ClientIp clientIp, String guestLockValue,
                                          ResumeAnalysisJobInput jobInput) {
        return new ResumeAnalysis(guestToken, clientIp, guestLockValue, jobInput);
    }

    private void applyJobInput(ResumeAnalysisJobInput jobInput) {
        this.jobPosition = jobInput.jobPosition();
        this.jdProvided = jobInput.hasJobDescription();
        this.jobDescription = this.jdProvided ? jobInput.jobDescription() : null;
        this.jobCareer = jobInput.jobCareer();
    }

    private void initializeProgress() {
        this.state = ResumeAnalysisState.PENDING;
        this.chargedTokenCount = 0;
        this.tokenChargeFailed = false;
        this.questionRetryCount = 0;
    }

    public void completeEvaluation(ResumeAnalysisEvaluation evaluation) {
        validateCurrentState(ResumeAnalysisState.PENDING);
        applyDimensionScores(evaluation);
        this.totalScore = evaluation.totalScore();
        this.totalFeedback = evaluation.totalFeedback();
        this.state = ResumeAnalysisState.EVALUATION_COMPLETED;
        LocalDateTime now = LocalDateTime.now();
        this.evaluationCompletedAt = now;
        this.questionStartedAt = now;
    }

    private void applyDimensionScores(ResumeAnalysisEvaluation evaluation) {
        DimensionScore problemSolving = evaluation.problemSolving();
        this.problemSolvingScore = problemSolving.score();
        this.problemSolvingReason = problemSolving.reason();
        this.problemSolvingImprovements = problemSolving.improvements();
        DimensionScore projectExperience = evaluation.projectExperience();
        this.projectExperienceScore = projectExperience.score();
        this.projectExperienceReason = projectExperience.reason();
        this.projectExperienceImprovements = projectExperience.improvements();
        DimensionScore technicalSkills = evaluation.technicalSkills();
        this.technicalSkillsScore = technicalSkills.score();
        this.technicalSkillsReason = technicalSkills.reason();
        this.technicalSkillsImprovements = technicalSkills.improvements();
        DimensionScore softSkills = evaluation.softSkills();
        this.softSkillsScore = softSkills.score();
        this.softSkillsReason = softSkills.reason();
        this.softSkillsImprovements = softSkills.improvements();
        DimensionScore jdFit = evaluation.jdFit();
        if (jdFit == null) {
            return;
        }
        this.jdFitScore = jdFit.score();
        this.jdFitReason = jdFit.reason();
        this.jdFitImprovements = jdFit.improvements();
    }

    public void failEvaluation(ResumeAnalysisFailureReason reason) {
        validateCurrentState(ResumeAnalysisState.PENDING);
        this.state = ResumeAnalysisState.EVALUATION_FAILED;
        this.failureReason = reason;
    }

    public void completeQuestions() {
        validateCurrentState(ResumeAnalysisState.EVALUATION_COMPLETED);
        this.state = ResumeAnalysisState.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void failQuestions(ResumeAnalysisFailureReason reason) {
        validateCurrentState(ResumeAnalysisState.EVALUATION_COMPLETED);
        this.state = ResumeAnalysisState.QUESTION_FAILED;
        this.failureReason = reason;
    }

    public void restoreForQuestionRetry() {
        validateCurrentState(ResumeAnalysisState.QUESTION_FAILED);
        this.state = ResumeAnalysisState.EVALUATION_COMPLETED;
        this.failureReason = null;
        this.questionRetryCount = this.questionRetryCount + 1;
        this.questionStartedAt = LocalDateTime.now();
    }

    private void validateCurrentState(ResumeAnalysisState expected) {
        if (this.state != expected) {
            throw new IllegalStateException(
                    "이력서 분석 상태가 %s가 아닙니다. currentState=%s".formatted(expected, this.state));
        }
    }

    public boolean isGuest() {
        return member == null;
    }

    public boolean isOwner(Long memberId) {
        if (member == null || memberId == null) {
            return false;
        }
        return memberId.equals(member.getId());
    }

    public boolean isSameGuestToken(String guestToken) {
        return isGuest() && guestToken != null && guestToken.equals(this.guestToken);
    }

    public boolean isQuestionRetryable(boolean sourceTextExists) {
        return state == ResumeAnalysisState.QUESTION_FAILED
                && questionRetryCount < MAX_QUESTION_RETRY
                && sourceTextExists;
    }
}
