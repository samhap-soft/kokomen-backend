package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;

public class SystemDesignAnswerFixtureBuilder {

    private SystemDesignQuestion question;
    private String content;
    private SystemDesignDiagram diagram;
    private AnswerRank answerRank;
    private String feedback;

    public static SystemDesignAnswerFixtureBuilder builder() {
        return new SystemDesignAnswerFixtureBuilder();
    }

    public SystemDesignAnswerFixtureBuilder question(SystemDesignQuestion question) {
        this.question = question;
        return this;
    }

    public SystemDesignAnswerFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public SystemDesignAnswerFixtureBuilder diagram(SystemDesignDiagram diagram) {
        this.diagram = diagram;
        return this;
    }

    public SystemDesignAnswerFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public SystemDesignAnswerFixtureBuilder feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public SystemDesignAnswer build() {
        return new SystemDesignAnswer(
                question != null ? question : SystemDesignQuestionFixtureBuilder.builder().build(),
                content != null ? content : "API 서버 앞에 캐시를 두어 DB 조회를 줄여요.",
                diagram != null ? diagram : SystemDesignDiagram.empty(),
                answerRank != null ? answerRank : AnswerRank.B,
                feedback != null ? feedback : "조회 경로에 캐시를 둔 근거가 분명해요."
        );
    }
}
