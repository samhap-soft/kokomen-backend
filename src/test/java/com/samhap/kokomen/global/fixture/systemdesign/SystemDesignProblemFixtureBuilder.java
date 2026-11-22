package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import java.util.concurrent.atomic.AtomicLong;

public class SystemDesignProblemFixtureBuilder {

    // system_design_problem.code가 UNIQUE라 기본 code는 빌드마다 다르게 만든다.
    private static final AtomicLong CODE_SEQUENCE = new AtomicLong();

    private String code;
    private SystemDesignDifficulty difficulty;
    private SystemDesignProblemState state;
    private String title;
    private String description;
    private String difficultyComment;
    private SystemDesignDiagram starterDiagram;

    public static SystemDesignProblemFixtureBuilder builder() {
        return new SystemDesignProblemFixtureBuilder();
    }

    public SystemDesignProblemFixtureBuilder code(String code) {
        this.code = code;
        return this;
    }

    public SystemDesignProblemFixtureBuilder difficulty(SystemDesignDifficulty difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    public SystemDesignProblemFixtureBuilder state(SystemDesignProblemState state) {
        this.state = state;
        return this;
    }

    public SystemDesignProblemFixtureBuilder title(String title) {
        this.title = title;
        return this;
    }

    public SystemDesignProblemFixtureBuilder description(String description) {
        this.description = description;
        return this;
    }

    public SystemDesignProblemFixtureBuilder difficultyComment(String difficultyComment) {
        this.difficultyComment = difficultyComment;
        return this;
    }

    public SystemDesignProblemFixtureBuilder starterDiagram(SystemDesignDiagram starterDiagram) {
        this.starterDiagram = starterDiagram;
        return this;
    }

    public SystemDesignProblem build() {
        return new SystemDesignProblem(
                code != null ? code : "PROBLEM_" + CODE_SEQUENCE.incrementAndGet(),
                difficulty != null ? difficulty : SystemDesignDifficulty.BASIC,
                state != null ? state : SystemDesignProblemState.ACTIVE,
                title != null ? title : "URL 단축기를 설계해 보세요",
                description != null ? description : "긴 URL을 받아 짧은 URL을 돌려주는 서비스예요.",
                difficultyComment != null ? difficultyComment : "리다이렉트 경로와 단축 키 생성을 설명하면 좋은 평가를 받아요.",
                starterDiagram
        );
    }
}
