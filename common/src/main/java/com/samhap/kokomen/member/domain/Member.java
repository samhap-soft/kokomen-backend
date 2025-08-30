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

    @Column(name = "profile_completed", nullable = false)
    private Boolean profileCompleted;

    public Member(Long kakaoId, String nickname) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.score = 0;
        this.profileCompleted = false;
    }

    public void addScore(Integer addendScore) {
        if (this.score + addendScore < 0) {
            this.score = 0;
            return;
        }
        this.score += addendScore;
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
