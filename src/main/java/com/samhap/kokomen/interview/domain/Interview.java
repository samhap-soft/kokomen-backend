package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Interview extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "total_feedback", length = 2_000)
    private String totalFeedback;

    @Column(name = "total_score")
    private Integer totalScore;

    public Interview(Member member) {
        this.member = member;
    }

    public void evaluate(String totalFeedback, Integer totalScore) {
        this.totalFeedback = totalFeedback;
        this.totalScore = totalScore;
    }
}
