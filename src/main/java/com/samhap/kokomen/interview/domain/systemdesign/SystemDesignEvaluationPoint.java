package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가 포인트. 마지막 답변 채점이 포인트마다 MET/NOT_MET을 판정한다. code는 판정 필드 이름(point_{code 소문자})의 바탕이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_evaluation_point", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_evaluation_point_code", columnNames = {"problem_id", "code"}),
        @UniqueConstraint(name = "uk_system_design_evaluation_point_order",
                columnNames = {"problem_id", "display_order"})
})
public class SystemDesignEvaluationPoint extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private SystemDesignProblem problem;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public SystemDesignEvaluationPoint(SystemDesignProblem problem, String code, String description,
                                       Integer displayOrder) {
        this.problem = problem;
        this.code = code;
        this.description = description;
        this.displayOrder = displayOrder;
    }
}
