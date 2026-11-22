package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;

public class SystemDesignEvaluationResultFixtureBuilder {

    private SystemDesignInterview systemDesignInterview;
    private SystemDesignEvaluationPoint evaluationPoint;
    private Boolean met;

    public static SystemDesignEvaluationResultFixtureBuilder builder() {
        return new SystemDesignEvaluationResultFixtureBuilder();
    }

    public SystemDesignEvaluationResultFixtureBuilder systemDesignInterview(
            SystemDesignInterview systemDesignInterview) {
        this.systemDesignInterview = systemDesignInterview;
        return this;
    }

    public SystemDesignEvaluationResultFixtureBuilder evaluationPoint(SystemDesignEvaluationPoint evaluationPoint) {
        this.evaluationPoint = evaluationPoint;
        return this;
    }

    public SystemDesignEvaluationResultFixtureBuilder met(boolean met) {
        this.met = met;
        return this;
    }

    public SystemDesignEvaluationResult build() {
        return new SystemDesignEvaluationResult(
                systemDesignInterview != null ? systemDesignInterview
                        : SystemDesignInterviewFixtureBuilder.builder().build(),
                evaluationPoint != null ? evaluationPoint : SystemDesignEvaluationPointFixtureBuilder.builder().build(),
                met != null ? met : true
        );
    }
}
