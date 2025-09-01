package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenPurchaseService {

    private final TokenPurchaseRepository tokenPurchaseRepository;

    @Transactional
    public void saveTokenPurchase(TokenPurchase tokenPurchase) {
        tokenPurchaseRepository.save(tokenPurchase);
    }

    @Transactional
    public void usePaidToken(Long memberId) {
        Optional<TokenPurchase> usableToken = tokenPurchaseRepository.findFirstUsableTokenByState(memberId, TokenPurchaseState.USABLE);
        if (usableToken.isPresent()) {
            usableToken.get().useToken();
            return;
        }

        Optional<TokenPurchase> refundableToken = tokenPurchaseRepository.findFirstUsableTokenByState(memberId, TokenPurchaseState.REFUNDABLE);
        if (refundableToken.isPresent()) {
            refundableToken.get().useToken();
            return;
        }

        throw new BadRequestException("사용 가능한 유료 토큰이 없습니다.");
    }

    @Transactional(readOnly = true)
    public TokenPurchase readTokenPurchaseById(Long tokenPurchaseId) {
        return tokenPurchaseRepository.findById(tokenPurchaseId)
                .orElseThrow(() -> new BadRequestException("토큰 구매 내역을 찾을 수 없습니다."));
    }

    @Transactional
    public void refundTokenPurchase(TokenPurchase tokenPurchase, RefundReasonCode refundReasonCode, String refundReasonText) {
        tokenPurchase.refund(refundReasonCode, refundReasonText);
    }

    @Transactional(readOnly = true)
    public Page<TokenPurchase> findTokenPurchasesByMemberId(Long memberId, TokenPurchaseState state, Pageable pageable) {
        if (state == null) {
            return tokenPurchaseRepository.findByMemberId(memberId, pageable);
        }
        return tokenPurchaseRepository.findByMemberIdAndState(memberId, state, pageable);
    }
}
