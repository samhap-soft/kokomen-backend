package com.samhap.kokomen.global.fixture.member;

import com.samhap.kokomen.member.domain.Member;

public class MemberFixtureBuilder {

    private Long id;
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

    public MemberFixtureBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public MemberFixtureBuilder score(Integer score) {
        this.score = score;
        return this;
    }

    public Member build() {
        return new Member(
                id,
                nickname != null ? nickname : "오상훈",
                score != null ? score : 0,
                profileCompleted != null ? profileCompleted : false
        );
    }
}
