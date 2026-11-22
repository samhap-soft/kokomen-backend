package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

public class SystemDesignInterviewFixtureBuilder {

    private Interview interview;
    private SystemDesignProblem problem;
    private SystemDesignStage stage;
    private SystemDesignDiagram currentDiagram;
    private Integer reportScore;

    public static SystemDesignInterviewFixtureBuilder builder() {
        return new SystemDesignInterviewFixtureBuilder();
    }

    public SystemDesignInterviewFixtureBuilder interview(Interview interview) {
        this.interview = interview;
        return this;
    }

    public SystemDesignInterviewFixtureBuilder problem(SystemDesignProblem problem) {
        this.problem = problem;
        return this;
    }

    public SystemDesignInterviewFixtureBuilder stage(SystemDesignStage stage) {
        this.stage = stage;
        return this;
    }

    public SystemDesignInterviewFixtureBuilder currentDiagram(SystemDesignDiagram currentDiagram) {
        this.currentDiagram = currentDiagram;
        return this;
    }

    public SystemDesignInterviewFixtureBuilder reportScore(Integer reportScore) {
        this.reportScore = reportScore;
        return this;
    }

    public SystemDesignInterview build() {
        SystemDesignProblem resolvedProblem =
                problem != null ? problem : SystemDesignProblemFixtureBuilder.builder().build();
        return new SystemDesignInterview(
                interview != null ? interview : InterviewFixtureBuilder.systemDesign().build(),
                resolvedProblem,
                stage != null ? stage : SystemDesignStage.REQUIREMENTS,
                currentDiagram != null ? currentDiagram : resolvedProblem.startingDiagram(),
                reportScore
        );
    }
}
