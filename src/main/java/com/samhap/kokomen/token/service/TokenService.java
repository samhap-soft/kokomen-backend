package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
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
            throw new InternalServerErrorException("유료 토큰 구매에 실패했습니다. memberId: " + memberId);
        }
    }

    @Transactional
    public void setFreeTokens(Long memberId, int count) {
        Token freeToken = readTokenByMemberIdAndType(memberId, TokenType.FREE);
        freeToken.setTokenCount(count);
    }

    @Transactional
    public void useFreeToken(Long memberId) {
        useTokens(memberId, TokenType.FREE, 1, "사용할 수 있는 토큰이 없습니다.");
    }

    @Transactional
    public void useFreeTokens(Long memberId, int count) {
        useTokens(memberId, TokenType.FREE, count, "사용할 수 있는 토큰이 부족합니다.");
    }

    @Transactional
    public void usePaidToken(Long memberId) {
        useTokens(memberId, TokenType.PAID, 1, "사용할 수 있는 토큰이 없습니다.");
    }

    @Transactional
    public void usePaidTokens(Long memberId, int count) {
        useTokens(memberId, TokenType.PAID, count, "사용할 수 있는 토큰이 부족합니다.");
    }

    /**
     * 읽어 둔 잔액으로 계산한 값을 쓰지 않고 DB에서 조건부로 차감한다. 웹훅·복구의 지급(grantPurchasedTokens)과
     * 일일 무료 토큰 재충전은 token 분산 락 없이 잔액을 바꾸므로, Token을 읽어 고친 값을 커밋 때 쓰면 그 사이
     * 커밋된 변경을 덮어쓴다. 이 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서 앞서 읽은 Token의
     * tokenCount는 차감 전 값으로 남는다.
     */
    private void useTokens(Long memberId, TokenType type, int count, String shortageMessage) {
        if (count <= 0) {
            throw new BadRequestException("사용할 토큰 수는 0보다 커야 합니다.");
        }
        int updatedRows = tokenRepository.decrementTokenCountModifying(memberId, type, count);
        if (updatedRows == 0) {
            throw new BadRequestException(shortageMessage);
        }
    }

    @Transactional
    public void refundPaidTokenCount(Long memberId, int count) {
        int updatedRows = tokenRepository.decrementTokenCountModifying(memberId, TokenType.PAID, count);
        if (updatedRows == 0) {
            throw new InternalServerErrorException("유료 토큰 환불에 실패했습니다. memberId: " + memberId);
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
                .orElseThrow(() -> new BadRequestException("해당 유형의 토큰이 존재하지 않습니다. type: " + type));
    }
}
