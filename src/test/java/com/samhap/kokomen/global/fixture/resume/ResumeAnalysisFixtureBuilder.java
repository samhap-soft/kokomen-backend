package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import java.util.UUID;

/**
 * 기본값은 "JD 없음 + PENDING + 게스트"다. 검증이 가장 까다로운 조합(차원 4개·미완료·소유자 없음)을
 * zero-config 기본으로 두어, 아무것도 지정하지 않은 픽스처가 가장 약한 전제에서 출발하게 한다.
 * 상태는 전부 엔티티 전이 API를 통과하므로 불가능한 상태의 픽스처를 만들 수 없다.
 */
public class ResumeAnalysisFixtureBuilder {

    private static final String DEFAULT_GUEST_IP = "11.22.33.99";
    private static final String DEFAULT_JOB_POSITION = "백엔드 개발자";
    private static final String DEFAULT_JOB_CAREER = "신입";
    private static final String DEFAULT_TOTAL_FEEDBACK = "전반적으로 우수한 지원자입니다.";
    private static final int DEFAULT_JD_FIT_SCORE = 70;

    private Member member;
    private boolean guestRequested;
    private String guestToken;
    private String guestIp;
    private MemberResume resume;
    private MemberPortfolio portfolio;
    private String jobPosition;
    private String jobDescription;
    private String jobCareer;
    private boolean billingRequired;
    private ResumeAnalysisState state = ResumeAnalysisState.PENDING;
    private ResumeAnalysisFailureReason failureReason;
    private int questionRetryCount;
    private DimensionScore problemSolving;
    private DimensionScore projectExperience;
    private DimensionScore technicalSkills;
    private DimensionScore softSkills;
    private DimensionScore jdFit;
    private Integer allDimensionsScore;
    private Integer totalScore;
    private String totalFeedback;

    public static ResumeAnalysisFixtureBuilder builder() {
        return new ResumeAnalysisFixtureBuilder();
    }

    public ResumeAnalysisFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public ResumeAnalysisFixtureBuilder guest(String guestToken, String guestIp) {
        this.guestRequested = true;
        this.guestToken = guestToken;
        this.guestIp = guestIp;
        return this;
    }

    public ResumeAnalysisFixtureBuilder guest() {
        return guest(UUID.randomUUID().toString(), DEFAULT_GUEST_IP);
    }

    public ResumeAnalysisFixtureBuilder resume(MemberResume resume) {
        this.resume = resume;
        return this;
    }

    public ResumeAnalysisFixtureBuilder portfolio(MemberPortfolio portfolio) {
        this.portfolio = portfolio;
        return this;
    }

    public ResumeAnalysisFixtureBuilder jobPosition(String jobPosition) {
        this.jobPosition = jobPosition;
        return this;
    }

    public ResumeAnalysisFixtureBuilder jobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
        return this;
    }

    public ResumeAnalysisFixtureBuilder jobCareer(String jobCareer) {
        this.jobCareer = jobCareer;
        return this;
    }

    public ResumeAnalysisFixtureBuilder billingRequired(boolean billingRequired) {
        this.billingRequired = billingRequired;
        return this;
    }

    public ResumeAnalysisFixtureBuilder state(ResumeAnalysisState state) {
        this.state = state;
        return this;
    }

    public ResumeAnalysisFixtureBuilder failureReason(ResumeAnalysisFailureReason failureReason) {
        this.failureReason = failureReason;
        return this;
    }

    public ResumeAnalysisFixtureBuilder questionRetryCount(int questionRetryCount) {
        this.questionRetryCount = questionRetryCount;
        return this;
    }

    public ResumeAnalysisFixtureBuilder problemSolving(DimensionScore problemSolving) {
        this.problemSolving = problemSolving;
        return this;
    }

    public ResumeAnalysisFixtureBuilder projectExperience(DimensionScore projectExperience) {
        this.projectExperience = projectExperience;
        return this;
    }

    public ResumeAnalysisFixtureBuilder technicalSkills(DimensionScore technicalSkills) {
        this.technicalSkills = technicalSkills;
        return this;
    }

    public ResumeAnalysisFixtureBuilder softSkills(DimensionScore softSkills) {
        this.softSkills = softSkills;
        return this;
    }

    public ResumeAnalysisFixtureBuilder jdFit(DimensionScore jdFit) {
        this.jdFit = jdFit;
        return this;
    }

    public ResumeAnalysisFixtureBuilder allDimensions(int score) {
        this.allDimensionsScore = score;
        return this;
    }

    public ResumeAnalysisFixtureBuilder totalScore(Integer totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public ResumeAnalysisFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public ResumeAnalysis build() {
        validateOwner();
        ResumeAnalysis analysis = (member != null)
                ? ResumeAnalysis.forMember(member, resume, portfolio, jobInput(), billingRequired)
                : ResumeAnalysis.forGuest(guestToken(), new ClientIp(guestIp()), guestLockValue(), jobInput());
        applyState(analysis);
        return analysis;
    }

    private void validateOwner() {
        if (member != null && guestRequested) {
            throw new IllegalStateException("회원과 게스트를 동시에 지정할 수 없습니다.");
        }
    }

    private void applyState(ResumeAnalysis analysis) {
        if (state == ResumeAnalysisState.PENDING) {
            return;
        }
        if (state == ResumeAnalysisState.EVALUATION_FAILED) {
            analysis.failEvaluation(failureReasonOrDefault(ResumeAnalysisFailureReason.EVALUATION_LLM));
            return;
        }
        analysis.completeEvaluation(buildEvaluation());
        if (state == ResumeAnalysisState.QUESTION_FAILED) {
            analysis.failQuestions(failureReasonOrDefault(ResumeAnalysisFailureReason.QUESTION_LLM));
            applyQuestionRetryCount(analysis);
        } else if (state == ResumeAnalysisState.COMPLETED) {
            analysis.completeQuestions();
        }
    }

    private void applyQuestionRetryCount(ResumeAnalysis analysis) {
        for (int retry = 0; retry < questionRetryCount; retry++) {
            analysis.restoreForQuestionRetry();
            analysis.failQuestions(failureReasonOrDefault(ResumeAnalysisFailureReason.QUESTION_LLM));
        }
    }

    private ResumeAnalysisEvaluation buildEvaluation() {
        DimensionScore jd = (jobDescription == null)
                ? null
                : (jdFit != null ? jdFit : DimensionScoreFixture.of(DEFAULT_JD_FIT_SCORE));
        ResumeAnalysisWeights weights = (jd == null)
                ? ResumeAnalysisWeights.JD_ABSENT : ResumeAnalysisWeights.JD_PROVIDED;
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                orDefault(problemSolving, 90), orDefault(projectExperience, 80),
                orDefault(technicalSkills, 70), orDefault(softSkills, 60), jd, null, totalFeedback());
        return evaluation.withTotalScore(
                totalScore != null ? totalScore : weights.calculateTotalScore(evaluation));
    }

    private DimensionScore orDefault(DimensionScore dimension, int defaultScore) {
        if (dimension != null) {
            return dimension;
        }
        if (allDimensionsScore != null) {
            return DimensionScoreFixture.of(allDimensionsScore);
        }
        return DimensionScoreFixture.of(defaultScore);
    }

    private ResumeAnalysisFailureReason failureReasonOrDefault(ResumeAnalysisFailureReason defaultReason) {
        return failureReason != null ? failureReason : defaultReason;
    }

    private ResumeAnalysisJobInput jobInput() {
        return new ResumeAnalysisJobInput(
                jobPosition != null ? jobPosition : DEFAULT_JOB_POSITION,
                jobDescription,
                jobCareer != null ? jobCareer : DEFAULT_JOB_CAREER);
    }

    private String guestToken() {
        return guestToken != null ? guestToken : UUID.randomUUID().toString();
    }

    private String guestIp() {
        return guestIp != null ? guestIp : DEFAULT_GUEST_IP;
    }

    // 락 값은 guest_token과 반드시 다른 별개 UUID다.
    private String guestLockValue() {
        return UUID.randomUUID().toString();
    }

    private String totalFeedback() {
        return totalFeedback != null ? totalFeedback : DEFAULT_TOTAL_FEEDBACK;
    }
}
