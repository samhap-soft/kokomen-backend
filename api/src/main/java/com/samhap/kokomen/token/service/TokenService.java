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

    @Transactional
    public void addPaidTokens(Long memberId, int count) {
        int updatedRows = tokenRepository.incrementTokenCountModifying(memberId, TokenType.PAID, count);
        if (updatedRows == 0) {
            throw new IllegalStateException("유료 토큰 구매에 실패했습니다. memberId: " + memberId);
        }
    }

    @Transactional
    public void setFreeTokens(Long memberId, int count) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.setTokenCount(count);
    }

    @Transactional
    public void useFreeToken(Long memberId) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.useToken();
    }

    @Transactional
    public void useFreeTokens(Long memberId, int count) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.useTokens(count);
    }

    @Transactional
    public void usePaidToken(Long memberId) {
        Token paidToken = readTokenByMemberIdAndType(memberId, TokenType.PAID);
        paidToken.useToken();
    }

    @Transactional
    public void usePaidTokens(Long memberId, int count) {
        Token paidToken = readTokenByMemberIdAndType(memberId, TokenType.PAID);
        paidToken.useTokens(count);
    }

    @Transactional
    public void refundPaidTokenCount(Long memberId, int count) {
        int updatedRows = tokenRepository.decrementTokenCountModifying(memberId, TokenType.PAID, count);
        if (updatedRows == 0) {
            throw new IllegalStateException("유료 토큰 환불에 실패했습니다. memberId: " + memberId);
        }
    }

    @Transactional(readOnly = true)
    public void validateEnoughTokens(Long memberId, int requiredCount) {
        if (!hasEnoughTokens(memberId, requiredCount)) {
            throw new BadRequestException("토큰 갯수가 부족합니다.");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasEnoughTokens(Long memberId, int requiredCount) {
        return calculateTotalTokenCount(memberId) >= requiredCount;
    }

    private int calculateTotalTokenCount(Long memberId) {
        return readFreeTokenCount(memberId) + readPaidTokenCount(memberId);
    }

    @Transactional(readOnly = true)
    public int readFreeTokenCount(Long memberId) {
        return readTokenByMemberIdAndType(memberId, TokenType.FREE).getTokenCount();
    }

    @Transactional(readOnly = true)
    public int readPaidTokenCount(Long memberId) {
        return readTokenByMemberIdAndType(memberId, TokenType.PAID).getTokenCount();
    }

    @Transactional(readOnly = true)
    public Token readTokenByMemberIdAndType(Long memberId, TokenType type) {
        return tokenRepository.findByMemberIdAndType(memberId, type)
                .orElseThrow(() -> new IllegalStateException("해당 유형의 토큰이 존재하지 않습니다. type: " + type));
    }
}
