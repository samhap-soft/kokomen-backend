package com.samhap.kokomen.member.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
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

    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

    @Column(name = "nickname", length = 255)
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
        if (this.score + addendScore < 0) {
            this.score = 0;
            return;
        }
        this.score += addendScore;
    }

    public boolean hasEnoughTokenCount(int tokenCount) {
        return this.freeTokenCount >= tokenCount;
    }

    public void addFreeTokenCount(int count) {
        this.freeTokenCount += count;
    }

    public void useToken() {
        if (freeTokenCount <= 0) {
            throw new BadRequestException("토큰을 이미 모두 소진하였습니다.");
        }
        freeTokenCount--;
    }

    public void updateProfile(String nickname) {
        this.profileCompleted = true;
        this.nickname = nickname;
    }

    public void withdraw() {
        this.kakaoId = null;
        this.nickname = null;
        this.score = 0;
    }
}
