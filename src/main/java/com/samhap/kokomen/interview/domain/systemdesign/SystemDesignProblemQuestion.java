package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문제의 기준 질문 6개. question_order 1~3은 HIGH_LEVEL, 4~6은 DEEP_DIVE다.
 * targetPointCodes는 이 질문이 겨누는 평가 포인트 code 목록이다(시드 검증용, 실행 중에는 읽지 않는다).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_problem_question", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_problem_question_order",
                columnNames = {"problem_id", "question_order"})
})
public class SystemDesignProblemQuestion extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private SystemDesignProblem problem;

    @Column(name = "stage", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemDesignStage stage;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "target_point_codes", nullable = false, columnDefinition = "JSON")
    private List<String> targetPointCodes;

    public SystemDesignProblemQuestion(SystemDesignProblem problem, SystemDesignStage stage, Integer questionOrder,
                                       String content, List<String> targetPointCodes) {
        this.problem = problem;
        this.stage = stage;
        this.questionOrder = questionOrder;
        this.content = content;
        this.targetPointCodes = List.copyOf(targetPointCodes);
    }
}
