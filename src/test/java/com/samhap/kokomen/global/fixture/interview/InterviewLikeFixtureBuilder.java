package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.member.domain.Member;

public class InterviewLikeFixtureBuilder {

    private Long id;
    private Member member;
    private Interview interview;

    public static InterviewLikeFixtureBuilder builder() {
        return new InterviewLikeFixtureBuilder();
    }

    public InterviewLikeFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public InterviewLikeFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public InterviewLikeFixtureBuilder interview(Interview interview) {
        this.interview = interview;
        return this;
    }

    public InterviewLike build() {
        return new InterviewLike(
                id,
                member != null ? member : defaultMember(),
                interview != null ? interview : defaultInterview()
        );
    }

    private static Member defaultMember() {
        return MemberFixtureBuilder.builder()
                .build();
    }

    private static Interview defaultInterview() {
        return InterviewFixtureBuilder.builder()
                .build();
    }
}
