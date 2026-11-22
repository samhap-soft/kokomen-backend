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
 * 역질문으로 알아내야 하는 숨은 요구사항. code는 역질문 판정 필드 이름(requirement_{code 소문자})의 바탕이고,
 * name은 리포트(missed_requirements)에 보인다. 둘 다 문제 안에서 겹치지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_hidden_requirement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_hidden_requirement_code", columnNames = {"problem_id", "code"}),
        @UniqueConstraint(name = "uk_system_design_hidden_requirement_name", columnNames = {"problem_id", "name"}),
        @UniqueConstraint(name = "uk_system_design_hidden_requirement_order",
                columnNames = {"problem_id", "display_order"})
})
public class SystemDesignHiddenRequirement extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private SystemDesignProblem problem;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "answer", nullable = false, length = 500)
    private String answer;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public SystemDesignHiddenRequirement(SystemDesignProblem problem, String code, String name, String answer,
                                         Integer displayOrder) {
        this.problem = problem;
        this.code = code;
        this.name = name;
        this.answer = answer;
        this.displayOrder = displayOrder;
    }
}
