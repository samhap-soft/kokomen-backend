package com.samhap.kokomen.global.fixture.member;

import com.samhap.kokomen.member.domain.Member;

public class MemberFixtureBuilder {

    private Long id;
    private Long kakaoId;
    private String name;
    private Integer score;

    public static MemberFixtureBuilder builder() {
        return new MemberFixtureBuilder();
    }

    public MemberFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public MemberFixtureBuilder name(String name) {
        this.name = name;
        return this;
    }

    public MemberFixtureBuilder score(Integer score) {
        this.score = score;
        return this;
    }

    public MemberFixtureBuilder kakaoId(Long kakaoId) {
        this.kakaoId = kakaoId;
        return this;
    }

    public Member build() {
        return new Member(
                id,
                kakaoId != null ? kakaoId : 1L,
                name != null ? name : "오상훈",
                score != null ? score : 0
        );
    }
}
