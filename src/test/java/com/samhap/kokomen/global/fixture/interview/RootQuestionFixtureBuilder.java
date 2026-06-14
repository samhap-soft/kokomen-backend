package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import com.samhap.kokomen.interview.domain.RootQuestionType;

public class RootQuestionFixtureBuilder {

    private Long id;
    private Category category;
    private RootQuestionState rootQuestionState;
    private RootQuestionType questionType;
    private String title;
    private String content;
    private Integer questionOrder;

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

    public RootQuestionFixtureBuilder rootQuestionState(RootQuestionState rootQuestionState) {
        this.rootQuestionState = rootQuestionState;
        return this;
    }

    public RootQuestionFixtureBuilder questionType(RootQuestionType questionType) {
        this.questionType = questionType;
        return this;
    }

    public RootQuestionFixtureBuilder title(String title) {
        this.title = title;
        return this;
    }

    public RootQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public RootQuestionFixtureBuilder questionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
        return this;
    }

    public RootQuestion build() {
        RootQuestionType resolvedQuestionType = questionType != null ? questionType : RootQuestionType.GENERAL;
        Integer resolvedQuestionOrder = resolveQuestionOrder(resolvedQuestionType);
        return new RootQuestion(
                id,
                category != null ? category : Category.OPERATING_SYSTEM,
                rootQuestionState != null ? rootQuestionState : RootQuestionState.ACTIVE,
                resolvedQuestionType,
                title,
                content != null ? content : "프로세스와 스레드 차이 설명해주세요.",
                resolvedQuestionOrder
        );
    }

    private Integer resolveQuestionOrder(RootQuestionType resolvedQuestionType) {
        if (questionOrder != null) {
            return questionOrder;
        }
        return resolvedQuestionType == RootQuestionType.CODE ? null : 1;
    }
}
