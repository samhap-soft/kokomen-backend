package com.samhap.kokomen.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import org.junit.jupiter.api.Test;

class GeneratedQuestionTest {

    @Test
    void 분석용_질문은_analysis만_채우고_generation은_null이다() {
        ResumeAnalysis analysis = memberAnalysis();

        GeneratedQuestion question = GeneratedQuestion.forAnalysis(analysis, "질문 내용", "질문 이유", 1);

        assertAll(
                () -> assertThat(question.getAnalysis()).isSameAs(analysis),
                () -> assertThat(question.getGeneration()).isNull(),
                () -> assertThat(question.getContent()).isEqualTo("질문 내용"),
                () -> assertThat(question.getReason()).isEqualTo("질문 이유"),
                () -> assertThat(question.getQuestionOrder()).isEqualTo(1)
        );
    }

    @Test
    void 기존_생성_흐름의_질문은_generation만_채우고_analysis는_null이다() {
        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(
                MemberFixtureBuilder.builder().id(1L).build(), null, null, "3년");

        GeneratedQuestion question = new GeneratedQuestion(generation, "질문 내용", "질문 이유", 1);

        assertAll(
                () -> assertThat(question.getGeneration()).isSameAs(generation),
                () -> assertThat(question.getAnalysis()).isNull()
        );
    }

    @Test
    void 질문_내용이_컬럼_한도를_넘으면_말줄임표로_절단된다() {
        String tooLongContent = "가".repeat(GeneratedQuestion.CONTENT_MAX_LENGTH + 1);

        GeneratedQuestion question = GeneratedQuestion.forAnalysis(memberAnalysis(), tooLongContent, "이유", 1);

        assertAll(
                () -> assertThat(question.getContent()).hasSize(GeneratedQuestion.CONTENT_MAX_LENGTH),
                () -> assertThat(question.getContent()).endsWith("..."),
                () -> assertThat(question.getContent())
                        .startsWith("가".repeat(GeneratedQuestion.CONTENT_MAX_LENGTH - 3))
        );
    }

    @Test
    void 질문_이유가_컬럼_한도를_넘으면_말줄임표로_절단된다() {
        String tooLongReason = "나".repeat(GeneratedQuestion.REASON_MAX_LENGTH + 500);

        GeneratedQuestion question = GeneratedQuestion.forAnalysis(memberAnalysis(), "질문", tooLongReason, 1);

        assertAll(
                () -> assertThat(question.getReason()).hasSize(GeneratedQuestion.REASON_MAX_LENGTH),
                () -> assertThat(question.getReason()).endsWith("...")
        );
    }

    @Test
    void 한도와_같은_길이의_질문은_절단되지_않는다() {
        String exactContent = "다".repeat(GeneratedQuestion.CONTENT_MAX_LENGTH);

        GeneratedQuestion question = GeneratedQuestion.forAnalysis(memberAnalysis(), exactContent, "이유", 1);

        assertAll(
                () -> assertThat(question.getContent()).isEqualTo(exactContent),
                () -> assertThat(question.getContent()).doesNotEndWith("...")
        );
    }

    @Test
    void 이유가_null이면_null로_유지된다() {
        GeneratedQuestion question = GeneratedQuestion.forAnalysis(memberAnalysis(), "질문", null, 1);

        assertThat(question.getReason()).isNull();
    }

    private static ResumeAnalysis memberAnalysis() {
        return ResumeAnalysis.forMember(MemberFixtureBuilder.builder().id(1L).build(), null, null,
                new ResumeAnalysisJobInput("백엔드 개발자", null, "3년"), true);
    }
}
