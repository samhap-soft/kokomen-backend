package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import java.util.concurrent.atomic.AtomicInteger;

public class SystemDesignHiddenRequirementFixtureBuilder {

    // (문제, code)·(문제, 이름)·(문제, 표시 순서)가 UNIQUE라 기본값은 빌드마다 다르게 만든다.
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private SystemDesignProblem problem;
    private String code;
    private String name;
    private String answer;
    private Integer displayOrder;

    public static SystemDesignHiddenRequirementFixtureBuilder builder() {
        return new SystemDesignHiddenRequirementFixtureBuilder();
    }

    public SystemDesignHiddenRequirementFixtureBuilder problem(SystemDesignProblem problem) {
        this.problem = problem;
        return this;
    }

    public SystemDesignHiddenRequirementFixtureBuilder code(String code) {
        this.code = code;
        return this;
    }

    public SystemDesignHiddenRequirementFixtureBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SystemDesignHiddenRequirementFixtureBuilder answer(String answer) {
        this.answer = answer;
        return this;
    }

    public SystemDesignHiddenRequirementFixtureBuilder displayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public SystemDesignHiddenRequirement build() {
        int sequence = SEQUENCE.incrementAndGet();
        return new SystemDesignHiddenRequirement(
                problem != null ? problem : SystemDesignProblemFixtureBuilder.builder().build(),
                code != null ? code : "REQUIREMENT_" + sequence,
                name != null ? name : "요구사항 " + sequence,
                answer != null ? answer : "하루 평균 10만 건 정도예요.",
                displayOrder != null ? displayOrder : sequence
        );
    }
}
