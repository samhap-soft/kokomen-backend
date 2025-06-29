package com.samhap.kokomen.member.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member extends BaseEntity {

    public static final int DAILY_FREE_TOKEN_COUNT = 10;

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

    public Member(Long kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.score = 0;
        this.freeTokenCount = DAILY_FREE_TOKEN_COUNT;
    }

    public void addScore(Integer addendScore) {
        this.score += addendScore;
    }

    public boolean hasEnoughTokenCount(int maxQuestionCount) {
        return this.freeTokenCount >= maxQuestionCount;
    }

    public void addFreeTokenCount(int count) {
        this.freeTokenCount += count;
    }
}
