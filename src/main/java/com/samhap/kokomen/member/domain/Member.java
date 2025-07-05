package com.samhap.kokomen.member.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member", indexes = {
        @Index(name = "idx_member_score", columnList = "score")
})
public class Member extends BaseEntity {

    public static final int DAILY_FREE_TOKEN_COUNT = 20;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "free_token_count", nullable = false)
    private Integer freeTokenCount;

    @Column(name = "profile_completed", nullable = false)
    private Boolean profileCompleted;

    public Member(Long kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.score = 0;
        this.freeTokenCount = DAILY_FREE_TOKEN_COUNT;
        this.profileCompleted = false;
    }

    public void addScore(Integer addendScore) {
        this.score += addendScore;
    }

    public boolean hasEnoughTokenCount(int maxQuestionCount) {
        return this.freeTokenCount >= maxQuestionCount;
    }

    public void updateProfile(String nickname) {
        this.profileCompleted = true;
        this.nickname = nickname;
    }
}
