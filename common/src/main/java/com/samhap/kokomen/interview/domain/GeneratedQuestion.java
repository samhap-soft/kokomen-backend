package com.samhap.kokomen.interview.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "generated_question", indexes = {
        @Index(name = "idx_generated_question_generation_id", columnList = "generation_id")
})
public class GeneratedQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private ResumeQuestionGeneration generation;

    @Column(name = "content", nullable = false, length = 1_000)
    private String content;

    @Column(name = "reason", length = 1_000)
    private String reason;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    public GeneratedQuestion(ResumeQuestionGeneration generation, String content, String reason, Integer questionOrder) {
        this.generation = generation;
        this.content = content;
        this.reason = reason;
        this.questionOrder = questionOrder;
    }
}
