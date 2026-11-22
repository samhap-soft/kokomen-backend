package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.ANSWER_LENGTH;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설계 질문의 답변. 질문당 답변 하나를 DB가 강제한다(uk_system_design_answer_question_id).
 * diagram은 이 답변과 함께 낸 다이어그램의 정규화 JSON이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_answer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_answer_question_id", columnNames = {"question_id"})
})
public class SystemDesignAnswer extends BaseEntity {

    public static final int MAX_CONTENT_LENGTH = 3_000;
    public static final int MAX_FEEDBACK_LENGTH = 2_000;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SystemDesignQuestion question;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Convert(converter = SystemDesignDiagramConverter.class)
    @Column(name = "diagram", nullable = false, columnDefinition = "JSON")
    private SystemDesignDiagram diagram;

    @Column(name = "answer_rank", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AnswerRank answerRank;

    @Column(name = "feedback", nullable = false, length = MAX_FEEDBACK_LENGTH)
    private String feedback;

    public SystemDesignAnswer(SystemDesignQuestion question, String content, SystemDesignDiagram diagram,
                              AnswerRank answerRank, String feedback) {
        this.question = question;
        this.content = normalizeContent(content);
        this.diagram = diagram;
        this.answerRank = answerRank;
        // LLM 피드백은 길이를 믿지 않고 저장 직전에 컬럼 한도로 자른다.
        this.feedback = SystemDesignText.truncate(feedback, MAX_FEEDBACK_LENGTH);
    }

    /**
     * {@link SystemDesignText#normalize(String)}로 다듬은 뒤 1~{@value #MAX_CONTENT_LENGTH}자인지 본다. 다듬은 값을 저장·응답한다.
     */
    public static String normalizeContent(String raw) {
        String content = SystemDesignText.normalize(raw);
        if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException(ANSWER_LENGTH.getMessage());
        }
        return content;
    }
}
