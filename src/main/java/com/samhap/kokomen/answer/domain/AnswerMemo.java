package com.samhap.kokomen.answer.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "answer_memo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_answer_memo_answer_answer_memo_state", columnNames = {"answer_id", "answer_memo_state"})
})
public class AnswerMemo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content", nullable = false, length = 5_000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @Column(name = "answer_memo_visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    private AnswerMemoVisibility answerMemoVisibility;

    @Column(name = "answer_memo_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private AnswerMemoState answerMemoState;

    public AnswerMemo(String content, Answer answer, AnswerMemoVisibility answerMemoVisibility) {
        this(null, content, answer, answerMemoVisibility, AnswerMemoState.SUBMITTED);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateVisibility(AnswerMemoVisibility visibility) {
        this.answerMemoVisibility = visibility;
    }
}
