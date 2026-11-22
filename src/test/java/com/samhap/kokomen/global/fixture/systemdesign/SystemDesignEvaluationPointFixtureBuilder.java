package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import java.util.concurrent.atomic.AtomicInteger;

public class SystemDesignEvaluationPointFixtureBuilder {

    // (문제, code)·(문제, 표시 순서)가 UNIQUE라 기본값은 빌드마다 다르게 만든다.
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private SystemDesignProblem problem;
    private String code;
    private String description;
    private Integer displayOrder;

    public static SystemDesignEvaluationPointFixtureBuilder builder() {
        return new SystemDesignEvaluationPointFixtureBuilder();
    }

    public SystemDesignEvaluationPointFixtureBuilder problem(SystemDesignProblem problem) {
        this.problem = problem;
        return this;
    }

    public SystemDesignEvaluationPointFixtureBuilder code(String code) {
        this.code = code;
        return this;
    }

    public SystemDesignEvaluationPointFixtureBuilder description(String description) {
        this.description = description;
        return this;
    }

    public SystemDesignEvaluationPointFixtureBuilder displayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public SystemDesignEvaluationPoint build() {
        int sequence = SEQUENCE.incrementAndGet();
        return new SystemDesignEvaluationPoint(
                problem != null ? problem : SystemDesignProblemFixtureBuilder.builder().build(),
                code != null ? code : "POINT_" + sequence,
                description != null ? description : "조회가 생성보다 많다는 점을 근거로 캐시를 두었다",
                displayOrder != null ? displayOrder : sequence
        );
    }
}
