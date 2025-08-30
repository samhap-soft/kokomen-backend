package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.token.domain.TokenPrice;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.dto.RefundRequest;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenPurchaseResponse;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.external.PaymentClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenFacadeService {

    private final TokenService tokenService;
    private final TokenPurchaseService tokenPurchaseService;
    private final PaymentClient paymentClient;

    @Transactional
    public void purchaseTokens(Long memberId, TokenPurchaseRequest request) {
        int tokenCount = request.metadata().count();
        long totalAmount = request.totalAmount();

        validateTokenPrice(request);
        log.info("토큰 구매 요청 - memberId: {}, paymentKey: {}, tokenCount: {}, amount: {}", memberId, request.paymentKey(), tokenCount, totalAmount);

        paymentClient.confirmPayment(request.toConfirmRequest(memberId));
        tokenPurchaseService.saveTokenPurchase(request.toTokenPurchase(memberId));
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

    @Transactional
    public void useToken(Long memberId) {
        // TODO: 분산락으로 동시성 제어. (인터뷰 진행, 토큰 결제, 토큰 환불)
        if (tokenService.readFreeTokenCount(memberId) > 0) {
            tokenService.useFreeToken(memberId);
            return;
        }

        if (tokenService.readPaidTokenCount(memberId) > 0) {
            tokenService.usePaidToken(memberId);
            tokenPurchaseService.usePaidToken(memberId);
            return;
        }

        throw new BadRequestException("토큰을 이미 모두 소진하였습니다.");
    }

    @Transactional
    public void refundTokens(Long memberId, TokenRefundRequest tokenRefundRequest) {
        Long tokenPurchaseId = tokenRefundRequest.tokenPurchaseId();
        TokenPurchase tokenPurchase = tokenPurchaseService.readTokenPurchaseById(tokenPurchaseId);

        if (tokenPurchase.isNotOwnedBy(memberId)) {
            throw new BadRequestException("본인의 토큰 구매 내역만 환불할 수 있습니다.");
        }

        if (tokenPurchase.isNotRefundable()) {
            throw new BadRequestException("환불 불가능한 상태입니다. 환불 가능한 토큰은 사용하지 않은 상태여야 합니다.");
        }

        int refundTokenCount = tokenPurchase.getCount();
        String paymentKey = tokenPurchase.getPaymentKey();

        log.info("토큰 환불 요청 - memberId: {}, tokenPurchaseId: {}, paymentKey: {}, refundTokenCount: {}",
                memberId, tokenPurchaseId, paymentKey, refundTokenCount);

        paymentClient.refundPayment(new RefundRequest(paymentKey, tokenRefundRequest.reason()));

        tokenPurchaseService.refundTokenPurchase(tokenPurchase);
        tokenService.refundPaidTokenCount(memberId, refundTokenCount);

        log.info("토큰 환불 완료 - memberId: {}, tokenPurchaseId: {}, 차감된 토큰: {}", memberId, tokenPurchaseId, refundTokenCount);
    }

    @Transactional(readOnly = true)
    public List<TokenPurchaseResponse> readMyTokenPurchases(Long memberId, TokenPurchaseState state, Pageable pageable) {
        Page<TokenPurchase> tokenPurchases = tokenPurchaseService.findTokenPurchasesByMemberId(memberId, state, pageable);
        return tokenPurchases.stream()
                .map(TokenPurchaseResponse::from)
                .toList();
    }
}
