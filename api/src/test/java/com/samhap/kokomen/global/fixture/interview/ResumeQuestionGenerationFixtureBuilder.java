package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;

public class ResumeQuestionGenerationFixtureBuilder {

    private Member member;
    private MemberResume memberResume;
    private MemberPortfolio memberPortfolio;
    private String jobCareer;
    private ResumeQuestionGenerationState state;

    public static ResumeQuestionGenerationFixtureBuilder builder() {
        return new ResumeQuestionGenerationFixtureBuilder();
    }

    public ResumeQuestionGenerationFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public ResumeQuestionGenerationFixtureBuilder memberResume(MemberResume memberResume) {
        this.memberResume = memberResume;
        return this;
    }

    public ResumeQuestionGenerationFixtureBuilder memberPortfolio(MemberPortfolio memberPortfolio) {
        this.memberPortfolio = memberPortfolio;
        return this;
    }

    public ResumeQuestionGenerationFixtureBuilder jobCareer(String jobCareer) {
        this.jobCareer = jobCareer;
        return this;
    }

    public ResumeQuestionGenerationFixtureBuilder state(ResumeQuestionGenerationState state) {
        this.state = state;
        return this;
    }

    public ResumeQuestionGeneration build() {
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(
                member,
                memberResume,
                memberPortfolio,
                jobCareer != null ? jobCareer : "신입"
        );

        if (state == ResumeQuestionGenerationState.COMPLETED) {
            generation.complete();
        } else if (state == ResumeQuestionGenerationState.FAILED) {
            generation.fail();
        }

        return generation;
    }
}
