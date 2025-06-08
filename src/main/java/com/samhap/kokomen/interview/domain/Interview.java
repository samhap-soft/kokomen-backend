package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Interview extends BaseEntity {

    public static final int MIN_ALLOWED_MAX_QUESTION_COUNT = 3;
    public static final int MAX_ALLOWED_MAX_QUESTION_COUNT = 10;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_question_id", nullable = false)
    private RootQuestion rootQuestion;

    @Column(name = "max_question_count", nullable = false)
    private Integer maxQuestionCount;

    @Column(name = "total_feedback", length = 2_000)
    private String totalFeedback;

    @Column(name = "total_score")
    private Integer totalScore;

    public Interview(Member member, RootQuestion rootQuestion, Integer maxQuestionCount) {
        validateMaxQuestionCount(maxQuestionCount);
        this.member = member;
        this.rootQuestion = rootQuestion;
        this.maxQuestionCount = maxQuestionCount;
    }

    private void validateMaxQuestionCount(Integer maxQuestionCount) {
        if (maxQuestionCount < MIN_ALLOWED_MAX_QUESTION_COUNT || maxQuestionCount > MAX_ALLOWED_MAX_QUESTION_COUNT) {
            throw new BadRequestException("최대 질문 개수는 " + MIN_ALLOWED_MAX_QUESTION_COUNT + " 이상 " + MAX_ALLOWED_MAX_QUESTION_COUNT + " 이하이어야 합니다.");
        }
    }

    public void evaluate(String totalFeedback, Integer totalScore) {
        this.totalFeedback = totalFeedback;
        this.totalScore = totalScore;
    }
}
