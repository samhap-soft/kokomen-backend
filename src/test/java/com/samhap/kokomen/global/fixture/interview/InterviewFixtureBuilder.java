package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;
import java.time.LocalDateTime;

public class InterviewFixtureBuilder {

    private Long id;
    private boolean memberSet;
    private Member member;
    private RootQuestion rootQuestion;
    private Integer maxQuestionCount;
    private InterviewState interviewState;
    private InterviewMode interviewMode;
    private InterviewType interviewType;
    private String totalFeedback;
    private Integer totalScore;
    private Long likeCount;
    private Long viewCount;
    private LocalDateTime finishedAt;
    private String guestIp;

    public static InterviewFixtureBuilder builder() {
        return new InterviewFixtureBuilder();
    }

    public InterviewFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public InterviewFixtureBuilder member(Member member) {
        this.member = member;
        this.memberSet = true;
        return this;
    }

    public InterviewFixtureBuilder guestIp(String guestIp) {
        this.guestIp = guestIp;
        return this;
    }

    public InterviewFixtureBuilder rootQuestion(RootQuestion rootQuestion) {
        this.rootQuestion = rootQuestion;
        return this;
    }

    public InterviewFixtureBuilder maxQuestionCount(Integer maxQuestionCount) {
        this.maxQuestionCount = maxQuestionCount;
        return this;
    }

    public InterviewFixtureBuilder interviewState(InterviewState interviewState) {
        this.interviewState = interviewState;
        return this;
    }

    public InterviewFixtureBuilder interviewMode(InterviewMode interviewMode) {
        this.interviewMode = interviewMode;
        return this;
    }

    public InterviewFixtureBuilder interviewType(InterviewType interviewType) {
        this.interviewType = interviewType;
        return this;
    }

    public InterviewFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public InterviewFixtureBuilder totalScore(Integer totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public InterviewFixtureBuilder likeCount(Long likeCount) {
        this.likeCount = likeCount;
        return this;
    }

    public InterviewFixtureBuilder viewCount(Long viewCount) {
        this.viewCount = viewCount;
        return this;
    }

    public InterviewFixtureBuilder finishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public Interview build() {
        return new Interview(
                id,
                memberSet ? member : defaultMember(),
                rootQuestion != null ? rootQuestion : defaultRootQuestion(),
                maxQuestionCount != null ? maxQuestionCount : Interview.MIN_ALLOWED_MAX_QUESTION_COUNT,
                interviewState != null ? interviewState : InterviewState.IN_PROGRESS,
                interviewMode != null ? interviewMode : InterviewMode.TEXT,
                interviewType != null ? interviewType : InterviewType.CATEGORY_BASED,
                totalFeedback,
                totalScore,
                likeCount != null ? likeCount : 0L,
                viewCount != null ? viewCount : 0L,
                finishedAt,
                guestIp
        );
    }

    private static Member defaultMember() {
        return MemberFixtureBuilder.builder()
                .id(1L)
                .build();
    }

    private static RootQuestion defaultRootQuestion() {
        return RootQuestionFixtureBuilder.builder()
                .id(1L)
                .build();
    }
}
