package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.RootQuestion;

public class RootQuestionFixtureBuilder {

    private Long id;
    private Category category;
    private String content;
    private String voiceUrl;

    public static RootQuestionFixtureBuilder builder() {
        return new RootQuestionFixtureBuilder();
    }

    public RootQuestionFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public RootQuestionFixtureBuilder category(Category category) {
        this.category = category;
        return this;
    }

    public RootQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public RootQuestionFixtureBuilder voiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
        return this;
    }

    public RootQuestion build() {
        return new RootQuestion(
                id,
                category != null ? category : Category.OPERATING_SYSTEM,
                content != null ? content : "프로세스와 스레드 차이 설명해주세요.",
                voiceUrl != null ? voiceUrl : "mock-voice-url"
        );
    }
}
