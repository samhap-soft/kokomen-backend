package com.samhap.kokomen.global.fixture.resume;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberPortfolio;

public class MemberPortfolioFixtureBuilder {

    private Long id;
    private Member member;
    private String title;
    private String portfolioUrl;

    public static MemberPortfolioFixtureBuilder builder() {
        return new MemberPortfolioFixtureBuilder();
    }

    public MemberPortfolioFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public MemberPortfolioFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public MemberPortfolioFixtureBuilder title(String title) {
        this.title = title;
        return this;
    }

    public MemberPortfolioFixtureBuilder resumeUrl(String portfolioUrl) {
        this.portfolioUrl = portfolioUrl;
        return this;
    }

    public MemberPortfolio build() {
        return new MemberPortfolio(
                id,
                member,
                title != null ? title : "기본 포트폴리오 제목",
                portfolioUrl != null ? portfolioUrl : "https://example.com/resume.pdf"
        );
    }
}
