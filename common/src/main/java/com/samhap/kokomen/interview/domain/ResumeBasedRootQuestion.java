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
@Table(name = "resume_based_root_question", indexes = {
        @Index(name = "idx_resume_based_root_question_interview_id", columnList = "interview_id")
})
public class ResumeBasedRootQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "content", nullable = false, length = 1_000)
    private String content;

    @Column(name = "reason", length = 1_000)
    private String reason;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    public ResumeBasedRootQuestion(Interview interview, String content, String reason, Integer questionOrder) {
        this.interview = interview;
        this.content = content;
        this.reason = reason;
        this.questionOrder = questionOrder;
    }
}
