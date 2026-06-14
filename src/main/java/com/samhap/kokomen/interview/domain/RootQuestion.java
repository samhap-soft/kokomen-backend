package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "root_question", uniqueConstraints = {
        @UniqueConstraint(name = "uk_root_question_category_question_order", columnNames = {"category", "question_order"})
})
public class RootQuestion extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private RootQuestionState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private RootQuestionType questionType;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "question_order")
    private Integer questionOrder;

    public RootQuestion(Category category, String content) {
        this.category = category;
        this.state = RootQuestionState.ACTIVE;
        this.questionType = RootQuestionType.GENERAL;
        this.content = content;
    }

    public static RootQuestion forCode(Category category, String title, String content) {
        RootQuestion rootQuestion = new RootQuestion();
        rootQuestion.category = category;
        rootQuestion.state = RootQuestionState.ACTIVE;
        rootQuestion.questionType = RootQuestionType.CODE;
        rootQuestion.title = title;
        rootQuestion.content = content;
        return rootQuestion;
    }

    public String createInitialQuestionContent() {
        if (questionType == RootQuestionType.CODE) {
            return title + "\n\n" + content;
        }
        return content;
    }

    public boolean isCode() {
        return this.questionType == RootQuestionType.CODE;
    }
}
