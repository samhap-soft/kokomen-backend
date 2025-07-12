package com.samhap.kokomen.global.fixture.answer;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.fixture.interview.QuestionFixtureBuilder;
import com.samhap.kokomen.interview.domain.Question;

public class AnswerFixtureBuilder {

    private Long id;
    private Question question;
    private String content;
    private AnswerRank answerRank;
    private String feedback;
    private Integer likeCount;

    public static AnswerFixtureBuilder builder() {
        return new AnswerFixtureBuilder();
    }

    public AnswerFixtureBuilder id(Long id) {
        this.id = id;
        return this;
    }

    public AnswerFixtureBuilder question(Question question) {
        this.question = question;
        return this;
    }

    public AnswerFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public AnswerFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public AnswerFixtureBuilder feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public AnswerFixtureBuilder likeCount(Integer likeCount) {
        this.likeCount = likeCount;
        return this;
    }

    public Answer build() {
        return new Answer(
                id,
                question != null ? question : defaultQuestion(),
                content != null ? content : "프로세스는 무겁고 스레드는 경량입니다.",
                answerRank != null ? answerRank : AnswerRank.C,
                feedback != null ? feedback : "좀 더 자세하게 설명해주시면 좋겠네요.",
                likeCount != null ? likeCount : 0
        );
    }

    private static Question defaultQuestion() {
        return QuestionFixtureBuilder.builder()
                .build();
    }
}
