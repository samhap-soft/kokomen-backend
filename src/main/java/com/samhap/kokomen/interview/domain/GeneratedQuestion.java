package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
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
        @Index(name = "idx_generated_question_generation_id", columnList = "generation_id"),
        @Index(name = "idx_generated_question_analysis_id", columnList = "analysis_id")
})
public class GeneratedQuestion extends BaseEntity {

    public static final int CONTENT_MAX_LENGTH = 1_000;
    public static final int REASON_MAX_LENGTH = 1_000;

    private static final String ABBREVIATION_MARKER = "...";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id")
    private ResumeQuestionGeneration generation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private ResumeAnalysis analysis;

    @Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "reason", length = REASON_MAX_LENGTH)
    private String reason;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    public GeneratedQuestion(ResumeQuestionGeneration generation, String content, String reason, Integer questionOrder) {
        this.generation = generation;
        this.content = content;
        this.reason = reason;
        this.questionOrder = questionOrder;
    }

    private GeneratedQuestion(ResumeAnalysis analysis, String content, String reason, Integer questionOrder) {
        this.analysis = analysis;
        this.content = content;
        this.reason = reason;
        this.questionOrder = questionOrder;
    }

    /**
     * 툴 스키마의 maxLength를 신뢰하지 않고 영속화 직전에 방어적으로 절단한다.
     * 스키마를 지킨 응답이 컬럼 한도를 넘으면 Data too long으로 트랜잭션 전체가 롤백되고
     * 같은 데이터를 다시 넣는 재시도는 100% 재실패한다.
     */
    public static GeneratedQuestion forAnalysis(ResumeAnalysis analysis, String content, String reason,
                                                Integer questionOrder) {
        return new GeneratedQuestion(analysis, abbreviate(content, CONTENT_MAX_LENGTH),
                abbreviate(reason, REASON_MAX_LENGTH), questionOrder);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - ABBREVIATION_MARKER.length()) + ABBREVIATION_MARKER;
    }
}
