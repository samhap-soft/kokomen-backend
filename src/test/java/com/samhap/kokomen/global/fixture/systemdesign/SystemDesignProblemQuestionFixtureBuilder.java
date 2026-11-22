package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import java.util.List;

public class SystemDesignProblemQuestionFixtureBuilder {

    private SystemDesignProblem problem;
    private SystemDesignStage stage;
    private Integer questionOrder;
    private String content;
    private List<String> targetPointCodes;

    public static SystemDesignProblemQuestionFixtureBuilder builder() {
        return new SystemDesignProblemQuestionFixtureBuilder();
    }

    public SystemDesignProblemQuestionFixtureBuilder problem(SystemDesignProblem problem) {
        this.problem = problem;
        return this;
    }

    public SystemDesignProblemQuestionFixtureBuilder stage(SystemDesignStage stage) {
        this.stage = stage;
        return this;
    }

    public SystemDesignProblemQuestionFixtureBuilder questionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
        return this;
    }

    public SystemDesignProblemQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public SystemDesignProblemQuestionFixtureBuilder targetPointCodes(List<String> targetPointCodes) {
        this.targetPointCodes = targetPointCodes;
        return this;
    }

    public SystemDesignProblemQuestion build() {
        return new SystemDesignProblemQuestion(
                problem != null ? problem : SystemDesignProblemFixtureBuilder.builder().build(),
                stage != null ? stage : SystemDesignStage.HIGH_LEVEL,
                questionOrder != null ? questionOrder : 1,
                content != null ? content : "확인한 요구사항을 바탕으로 전체 구조를 그리고 설명해 주세요.",
                targetPointCodes != null ? targetPointCodes : List.of("READ_PATH_CACHE")
        );
    }
}
