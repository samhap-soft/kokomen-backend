package com.samhap.kokomen.answer.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.interview.domain.Question;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "answer", indexes = {
        @Index(name = "idx_answer_like_count", columnList = "like_count")
})
public class Answer extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "content", nullable = false, length = 2_000)
    private String content;

    @Column(name = "answer_rank", nullable = false)
    @Enumerated(EnumType.STRING)
    private AnswerRank answerRank;

    @Column(name = "feedback", length = 2_000)
    private String feedback;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    public Answer(Question question, String content, AnswerRank answerRank, String feedback) {
        this(null, question, content, answerRank, feedback, 0L);
    }
}
