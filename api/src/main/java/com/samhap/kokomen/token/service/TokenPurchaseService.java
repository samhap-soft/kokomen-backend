package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.token.domain.TokenPrice;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.external.PaymentClient;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenPurchaseService {

    private final PaymentClient paymentClient;
    private final TokenPurchaseRepository tokenPurchaseRepository;
    private final TokenService tokenService; // TODO: Facade로 분리

    @Transactional
    public void purchaseTokens(Long memberId, TokenPurchaseRequest request) {
        int tokenCount = request.metadata().count();
        long totalAmount = request.totalAmount();

        validateTokenPrice(request);
        log.info("토큰 구매 요청 - memberId: {}, paymentKey: {}, tokenCount: {}, amount: {}", memberId, request.paymentKey(), tokenCount, totalAmount);

        paymentClient.confirmPayment(request.toConfirmRequest(memberId));
        tokenPurchaseRepository.save(request.toTokenPurchase(memberId));

        tokenService.addPaidTokens(memberId, tokenCount);

        log.info("토큰 구매 완료 - memberId: {}, paymentKey: {}, 증가된 토큰: {}", memberId, request.paymentKey(), tokenCount);
    }

    private void validateTokenPrice(TokenPurchaseRequest request) {
        int tokenCount = request.metadata().count();
        long unitPrice = request.metadata().unitPrice();
        long totalAmount = request.totalAmount();
        String productName = request.metadata().productName();

        if (!"token".equals(productName)) {
            throw new BadRequestException("올바르지 않은 상품명입니다. 상품명은 'token'이어야 합니다.");
        }

        if (unitPrice != TokenPrice.SINGLE_TOKEN.getPrice()) {
            throw new BadRequestException(String.format("토큰 단가는 %d원이어야 합니다. 요청된 단가: %d원", TokenPrice.SINGLE_TOKEN.getPrice(), unitPrice));
        }

        long expectedTotalAmount = unitPrice * tokenCount;
        if (totalAmount != expectedTotalAmount) {
            throw new BadRequestException(String.format("총 금액이 올바르지 않습니다. 예상 금액: %d원 (단가 %d원 × %d개), 요청된 금액: %d원",
                    expectedTotalAmount, unitPrice, tokenCount, totalAmount));
        }
    }
}
