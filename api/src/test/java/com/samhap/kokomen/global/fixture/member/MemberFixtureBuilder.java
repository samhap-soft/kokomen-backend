package com.samhap.kokomen.global.fixture.member;

import com.samhap.kokomen.member.domain.Member;

public class MemberFixtureBuilder {

    private Long id;
    private Long kakaoId;
    private String nickname;
    private Integer score;
    private Boolean profileCompleted;

    public static MemberFixtureBuilder builder() {
        return new MemberFixtureBuilder();
    }

    public MemberFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public MemberFixtureBuilder kakaoId(Long kakaoId) {
        this.kakaoId = kakaoId;
        return this;
    }

    public MemberFixtureBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public MemberFixtureBuilder score(Integer score) {
        this.score = score;
        return this;
    }

    public MemberFixtureBuilder profileCompleted(Boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
        return this;
    }

    public Member build() {
        return new Member(
                id,
                kakaoId != null ? kakaoId : 1L,
                nickname != null ? nickname : "오상훈",
                score != null ? score : 0,
                profileCompleted != null ? profileCompleted : false
        );
    }
}
