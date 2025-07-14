package com.samhap.kokomen.global.fixture.answer;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;

public class AnswerMemoFixtureBuilder {

    private Long id;
    private String content;
    private Answer answer;
    private AnswerMemoVisibility answerMemoVisibility;
    private AnswerMemoState answerMemoState;

    public static AnswerMemoFixtureBuilder builder() {
        return new AnswerMemoFixtureBuilder();
    }

    public AnswerMemoFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public AnswerMemoFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public AnswerMemoFixtureBuilder answer(Answer answer) {
        this.answer = answer;
        return this;
    }

    public AnswerMemoFixtureBuilder answerMemoVisibility(AnswerMemoVisibility answerMemoVisibility) {
        this.answerMemoVisibility = answerMemoVisibility;
        return this;
    }

    public AnswerMemoFixtureBuilder answerMemoState(AnswerMemoState answerMemoState) {
        this.answerMemoState = answerMemoState;
        return this;
    }

    public AnswerMemo build() {
        return new AnswerMemo(
                id,
                content != null ? content : "오상훈의 메모",
                answer != null ? answer : defaultAnswer(),
                answerMemoVisibility != null ? answerMemoVisibility : AnswerMemoVisibility.PRIVATE,
                answerMemoState != null ? answerMemoState : AnswerMemoState.SUBMITTED
        );
    }

    private static Answer defaultAnswer() {
        return AnswerFixtureBuilder.builder()
                .build();
    }
}
