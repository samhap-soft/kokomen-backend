package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;

public class InterviewFixtureBuilder {

    private Long id;
    private Member member;
    private RootQuestion rootQuestion;
    private Integer maxQuestionCount;
    private String totalFeedback;
    private Integer totalScore;

    public static InterviewFixtureBuilder builder() {
        return new InterviewFixtureBuilder();
    }

    public InterviewFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public InterviewFixtureBuilder member(Member member) {
        this.member = member;
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

    public InterviewFixtureBuilder totalFeedback(String totalFeedback) {
        this.totalFeedback = totalFeedback;
        return this;
    }

    public InterviewFixtureBuilder totalScore(Integer totalScore) {
        this.totalScore = totalScore;
        return this;
    }

    public Interview build() {
        return new Interview(
                id,
                member != null ? member : defaultMember(),
                rootQuestion != null ? rootQuestion : defaultRootQuestion(),
                maxQuestionCount != null ? maxQuestionCount : Interview.MIN_ALLOWED_MAX_QUESTION_COUNT,
                totalFeedback,
                totalScore
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
