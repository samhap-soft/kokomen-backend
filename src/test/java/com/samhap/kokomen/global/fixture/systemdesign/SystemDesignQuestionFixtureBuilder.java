package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;

public class SystemDesignQuestionFixtureBuilder {

    private SystemDesignInterview systemDesignInterview;
    private SystemDesignProblemQuestion problemQuestion;
    private String content;

    public static SystemDesignQuestionFixtureBuilder builder() {
        return new SystemDesignQuestionFixtureBuilder();
    }

    public SystemDesignQuestionFixtureBuilder systemDesignInterview(SystemDesignInterview systemDesignInterview) {
        this.systemDesignInterview = systemDesignInterview;
        return this;
    }

    // 단계와 순번은 기준 질문에서 가져온다.
    public SystemDesignQuestionFixtureBuilder problemQuestion(SystemDesignProblemQuestion problemQuestion) {
        this.problemQuestion = problemQuestion;
        return this;
    }

    public SystemDesignQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public SystemDesignQuestion build() {
        return new SystemDesignQuestion(
                systemDesignInterview != null ? systemDesignInterview
                        : SystemDesignInterviewFixtureBuilder.builder().build(),
                problemQuestion != null ? problemQuestion : SystemDesignProblemQuestionFixtureBuilder.builder().build(),
                content != null ? content : "확인한 요구사항을 바탕으로 전체 구조를 그리고 설명해 주세요."
        );
    }
}
