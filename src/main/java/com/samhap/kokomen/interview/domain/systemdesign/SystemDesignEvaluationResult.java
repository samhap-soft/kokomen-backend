package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평가 포인트 판정 결과. 마지막 답변을 저장할 때 포인트마다 한 행씩 넣는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_evaluation_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_evaluation_result_point",
                columnNames = {"system_design_interview_id", "evaluation_point_id"})
}, indexes = {
        @Index(name = "idx_system_design_evaluation_result_point_id", columnList = "evaluation_point_id")
})
public class SystemDesignEvaluationResult extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_design_interview_id", nullable = false)
    private SystemDesignInterview systemDesignInterview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_point_id", nullable = false)
    private SystemDesignEvaluationPoint evaluationPoint;

    @Column(name = "met", nullable = false)
    private boolean met;

    public SystemDesignEvaluationResult(SystemDesignInterview systemDesignInterview,
                                        SystemDesignEvaluationPoint evaluationPoint, boolean met) {
        this.systemDesignInterview = systemDesignInterview;
        this.evaluationPoint = evaluationPoint;
        this.met = met;
    }
}
