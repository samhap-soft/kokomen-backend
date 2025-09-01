package com.samhap.kokomen.token.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "token", uniqueConstraints = {
    @UniqueConstraint(name = "uk_token_member_type", columnNames = {"member_id", "type"})
})
public class Token extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TokenType type;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;

    @Builder
    public Token(Long memberId, TokenType type, Integer tokenCount) {
        this.memberId = memberId;
        this.type = type;
        this.tokenCount = tokenCount != null ? tokenCount : 0;
    }

    public void addTokens(int count) {
        if (count < 0) {
            throw new IllegalStateException("추가할 토큰 수는 0보다 커야 합니다.");
        }
        this.tokenCount += count;
    }

    public void useToken() {
        if (tokenCount <= 0) {
            throw new BadRequestException("사용할 수 있는 토큰이 없습니다.");
        }
        this.tokenCount--;
    }

    public boolean hasTokens() {
        return tokenCount > 0;
    }

    public boolean hasEnoughTokens(int requiredCount) {
        return tokenCount >= requiredCount;
    }

    public void setTokenCount(int count) {
        if (count < 0) {
            throw new IllegalStateException("토큰 수는 0보다 작을 수 없습니다.");
        }
        this.tokenCount = count;
    }
}
