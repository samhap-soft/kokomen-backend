package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 설계 문제(V56, 시드로 넣는다). 면접 기록이 이 행과 자식 행을 참조하므로 한 번 넣은 내용은 고치지 않는다.
 * 내용을 바꿀 때는 새 code로 문제를 넣고 이전 문제를 INACTIVE로 돌린다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_problem", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_problem_code", columnNames = {"code"})
}, indexes = {
        @Index(name = "idx_system_design_problem_difficulty_state", columnList = "difficulty, state")
})
public class SystemDesignProblem extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "difficulty", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemDesignDifficulty difficulty;

    @Column(name = "state", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemDesignProblemState state;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 1_000)
    private String description;

    @Column(name = "difficulty_comment", nullable = false, length = 500)
    private String difficultyComment;

    // 없으면 NULL이다. 면접은 startingDiagram()으로 빈 다이어그램에서 시작한다.
    @Convert(converter = SystemDesignDiagramConverter.class)
    @Column(name = "starter_diagram", columnDefinition = "JSON")
    private SystemDesignDiagram starterDiagram;

    public SystemDesignProblem(String code, SystemDesignDifficulty difficulty, SystemDesignProblemState state,
                               String title, String description, String difficultyComment,
                               SystemDesignDiagram starterDiagram) {
        this.code = code;
        this.difficulty = difficulty;
        this.state = state;
        this.title = title;
        this.description = description;
        this.difficultyComment = difficultyComment;
        this.starterDiagram = starterDiagram;
    }

    public SystemDesignDiagram startingDiagram() {
        if (starterDiagram == null) {
            return SystemDesignDiagram.empty();
        }
        return starterDiagram;
    }
}
