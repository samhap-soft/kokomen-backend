package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 면접에서 실제로 낸 설계 질문. 외부 question_id는 questionOrder(면접 안 순번 1~6)이고 DB PK는 내보내지 않는다.
 * content는 기준 질문 그대로이거나 LLM이 다듬은 문장이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_question", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_question_order",
                columnNames = {"system_design_interview_id", "question_order"})
}, indexes = {
        @Index(name = "idx_system_design_question_problem_question_id", columnList = "problem_question_id")
})
public class SystemDesignQuestion extends BaseEntity {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_design_interview_id", nullable = false)
    private SystemDesignInterview systemDesignInterview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_question_id", nullable = false)
    private SystemDesignProblemQuestion problemQuestion;

    @Column(name = "stage", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemDesignStage stage;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    public SystemDesignQuestion(SystemDesignInterview systemDesignInterview,
                                SystemDesignProblemQuestion problemQuestion, String content) {
        this.systemDesignInterview = systemDesignInterview;
        this.problemQuestion = problemQuestion;
        this.stage = problemQuestion.getStage();
        this.questionOrder = problemQuestion.getQuestionOrder();
        // LLM이 다듬은 문장은 길이를 믿지 않고 저장 직전에 컬럼 한도로 자른다.
        this.content = SystemDesignText.truncate(content, MAX_CONTENT_LENGTH);
    }
}
