package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;

public class InterviewFixtureBuilder {

    private Long id;
    private Member member;
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
                totalFeedback,
                totalScore
        );
    }

    private static Member defaultMember() {
        return MemberFixtureBuilder.builder()
                .id(1L)
                .build();
    }
}
