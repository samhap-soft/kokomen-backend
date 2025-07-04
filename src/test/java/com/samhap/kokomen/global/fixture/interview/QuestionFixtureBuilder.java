package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;

public class QuestionFixtureBuilder {

    private Long id;
    private Interview interview;
    private String content;

    public static QuestionFixtureBuilder builder() {
        return new QuestionFixtureBuilder();
    }

    public QuestionFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public QuestionFixtureBuilder interview(Interview interview) {
        this.interview = interview;
        return this;
    }

    public QuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public Question build() {
        return new Question(
                id,
                interview != null ? interview : defaultInterview(),
                content != null ? content : "프로세스와 스레드 차이 설명해주세요."
        );
    }

    private static Interview defaultInterview() {
        return InterviewFixtureBuilder.builder()
                .id(1L)
                .build();
    }
}
