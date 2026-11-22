package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import java.util.List;

public class SystemDesignRequirementQuestionFixtureBuilder {

    private SystemDesignInterview systemDesignInterview;
    private Integer questionOrder;
    private String content;
    private String answer;
    private List<String> askedRequirementCodes;

    public static SystemDesignRequirementQuestionFixtureBuilder builder() {
        return new SystemDesignRequirementQuestionFixtureBuilder();
    }

    public SystemDesignRequirementQuestionFixtureBuilder systemDesignInterview(
            SystemDesignInterview systemDesignInterview) {
        this.systemDesignInterview = systemDesignInterview;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder questionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder answer(String answer) {
        this.answer = answer;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder askedRequirementCodes(List<String> askedRequirementCodes) {
        this.askedRequirementCodes = askedRequirementCodes;
        return this;
    }

    public SystemDesignRequirementQuestion build() {
        return new SystemDesignRequirementQuestion(
                systemDesignInterview != null ? systemDesignInterview
                        : SystemDesignInterviewFixtureBuilder.builder().build(),
                questionOrder != null ? questionOrder : 1,
                content != null ? content : "하루에 단축 URL이 몇 개나 만들어지나요?",
                answer != null ? answer : "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.",
                askedRequirementCodes != null ? askedRequirementCodes : List.of("DAILY_CREATION_VOLUME")
        );
    }
}
