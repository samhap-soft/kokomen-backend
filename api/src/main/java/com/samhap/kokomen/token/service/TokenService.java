package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final int DAILY_FREE_TOKEN_COUNT = 20;

    private final TokenRepository tokenRepository;

    @Transactional
    public void createTokensForNewMember(Long memberId) {
        Token freeToken = new Token(memberId, TokenType.FREE, DAILY_FREE_TOKEN_COUNT);
        Token paidToken = new Token(memberId, TokenType.PAID, 0);
        tokenRepository.save(freeToken);
        tokenRepository.save(paidToken);
    }

    public Token readTokenByMemberIdAndType(Long memberId, TokenType type) {
        return tokenRepository.findByMemberIdAndType(memberId, type)
                .orElseThrow(() -> new IllegalStateException("해당 유형의 토큰이 존재하지 않습니다. type: " + type));
    }

    @Transactional
    public void addFreeTokens(Long memberId, int count) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.addTokens(count);
    }

    @Transactional
    public void addPaidTokens(Long memberId, int count) {
        Token paidToken = readTokenByMemberIdAndType(memberId, TokenType.PAID);
        paidToken.addTokens(count);
    }

    @Transactional
    public void setFreeTokens(Long memberId, int count) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.setTokenCount(count);
    }

    @Transactional
    public void useToken(Long memberId) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        Token paidToken = readTokenByMemberIdAndType(memberId, TokenType.PAID);

        if (freeToken.hasTokens()) {
            freeToken.useToken();
            return;
        }

        if (paidToken.hasTokens()) {
            paidToken.useToken();
            return;
        }

        throw new BadRequestException("토큰을 이미 모두 소진하였습니다.");
    }

    public void validateEnoughTokens(Long memberId, int requiredCount) {
        if (!hasEnoughTokens(memberId, requiredCount)) {
            throw new BadRequestException("토큰 갯수가 부족합니다.");
        }
    }

    public boolean hasEnoughTokens(Long memberId, int requiredCount) {
        return getTotalTokenCount(memberId) >= requiredCount;
    }

    public int getTotalTokenCount(Long memberId) {
        return getFreeTokenCount(memberId) + getPaidTokenCount(memberId);
    }

    public int getFreeTokenCount(Long memberId) {
        return readTokenByMemberIdAndType(memberId, TokenType.FREE).getTokenCount();
    }

    public int getPaidTokenCount(Long memberId) {
        return readTokenByMemberIdAndType(memberId, TokenType.PAID).getTokenCount();
    }
}
