package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberResume;

public class MemberResumeFixtureBuilder {

    private Long id;
    private Member member;
    private String title;
    private String resumeUrl;

    public static MemberResumeFixtureBuilder builder() {
        return new MemberResumeFixtureBuilder();
    }

    public MemberResumeFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public MemberResumeFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public MemberResumeFixtureBuilder title(String title) {
        this.title = title;
        return this;
    }

    public MemberResumeFixtureBuilder resumeUrl(String resumeUrl) {
        this.resumeUrl = resumeUrl;
        return this;
    }

    public MemberResume build() {
        return new MemberResume(
                id,
                member,
                title != null ? title : "기본 이력서 제목",
                resumeUrl != null ? resumeUrl : "https://example.com/resume.pdf"
        );
    }
}
