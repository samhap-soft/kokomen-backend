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

    @Column(name = "content", nullable = false, length = 1_000)
    private String content;

    @Column(name = "question_order")
    private Integer questionOrder;

    public RootQuestion(Category category, String content) {
        this.category = category;
        this.state = RootQuestionState.ACTIVE;
        this.content = content;
    }
}
