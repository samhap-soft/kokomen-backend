package com.samhap.kokomen.global.fixture.answer;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerLike;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;

public class AnswerLikeFixtureBuilder {

    private Long id;
    private Member member;
    private Answer answer;

    public static AnswerLikeFixtureBuilder builder() {
        return new AnswerLikeFixtureBuilder();
    }

    public AnswerLikeFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public AnswerLikeFixtureBuilder member(Member member) {
        this.member = member;
        return this;
    }

    public AnswerLikeFixtureBuilder answer(Answer answer) {
        this.answer = answer;
        return this;
    }

    public AnswerLike build() {
        return new AnswerLike(
                id,
                member != null ? member : defaultMember(),
                answer != null ? answer : defaultAnswer()
        );
    }

    private static Member defaultMember() {
        return MemberFixtureBuilder.builder()
                .build();
    }

    private static Answer defaultAnswer() {
        return AnswerFixtureBuilder.builder()
                .build();
    }
}
