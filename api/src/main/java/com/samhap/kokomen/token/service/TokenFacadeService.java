package com.samhap.kokomen.token.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.dto.PaymentResponse;
import com.samhap.kokomen.token.dto.RefundRequest;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenPurchaseResponse;
import com.samhap.kokomen.token.dto.TokenPurchaseResponses;
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
        int tokenCount = getTokenCountFromProductName(request.productName());
        long totalAmount = request.price();

        validateTokenPrice(request);
        log.info("토큰 구매 요청 - memberId: {}, paymentKey: {}, tokenCount: {}, amount: {}", memberId, request.paymentKey(), tokenCount, totalAmount);

        PaymentResponse paymentResponse = paymentClient.confirmPayment(request.toConfirmRequest(memberId));
        tokenPurchaseService.saveTokenPurchase(request.toTokenPurchase(memberId, paymentResponse));
        tokenService.addPaidTokens(memberId, tokenCount);

        log.info("토큰 구매 완료 - memberId: {}, paymentKey: {}, 증가된 토큰: {}", memberId, request.paymentKey(), tokenCount);
    }

    private int getTokenCountFromProductName(String productName) {
        TokenProduct product = TokenProduct.valueOf(productName);
        return product.getTokenCount();
    }

    private void validateTokenPrice(TokenPurchaseRequest request) {
        String productName = request.productName();
        long totalAmount = request.price();

        try {
            TokenProduct product = TokenProduct.valueOf(productName);
            long expectedPrice = product.getPrice();

            if (totalAmount != expectedPrice) {
                throw new BadRequestException(String.format("총 금액이 올바르지 않습니다. 예상 금액: %d원, 요청된 금액: %d원",
                        expectedPrice, totalAmount));
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("올바르지 않은 상품명입니다. 유효한 상품: TOKEN_10, TOKEN_20, TOKEN_50, TOKEN_100, TOKEN_200");
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
    public void useTokens(Long memberId, int count) {
        int freeTokenCount = tokenService.readFreeTokenCount(memberId);
        int paidTokenCount = tokenService.readPaidTokenCount(memberId);

        if (freeTokenCount + paidTokenCount < count) {
            throw new BadRequestException("토큰 갯수가 부족합니다.");
        }

        int tokensFromFree = Math.min(count, freeTokenCount);
        int tokensFromPaid = count - tokensFromFree;

        if (tokensFromFree > 0) {
            tokenService.useFreeTokens(memberId, tokensFromFree);
        }

        if (tokensFromPaid > 0) {
            tokenService.usePaidTokens(memberId, tokensFromPaid);
            tokenPurchaseService.usePaidTokens(memberId, tokensFromPaid);
        }
    }

    @Transactional
    public void refundTokens(Long memberId, Long tokenPurchaseId, TokenRefundRequest request) {
        TokenPurchase tokenPurchase = tokenPurchaseService.readTokenPurchaseById(tokenPurchaseId);

        if (tokenPurchase.isNotOwnedBy(memberId)) {
            throw new BadRequestException("본인의 토큰 구매 내역만 환불할 수 있습니다.");
        }

        if (tokenPurchase.isNotRefundable()) {
            throw new BadRequestException("환불 불가능한 상태입니다. 환불 가능한 토큰은 사용하지 않은 상태여야 합니다.");
        }

        RefundReasonCode refundReasonCode = request.refundReasonCode();
        refundReasonCode.validateRefundReasonText(request.refundReasonText());

        int refundTokenCount = tokenPurchase.getPurchaseCount();
        String paymentKey = tokenPurchase.getPaymentKey();

        String refundReasonText = refundReasonCode.getRefundReason(request.refundReasonText());
        log.info("토큰 환불 요청 - memberId: {}, tokenPurchaseId: {}, paymentKey: {}, refundTokenCount: {}, refundReasonCode: {}, refundReasonText: {}",
                memberId, tokenPurchaseId, paymentKey, refundTokenCount, refundReasonCode, refundReasonText);

        paymentClient.refundPayment(new RefundRequest(paymentKey, refundReasonText));

        tokenPurchaseService.refundTokenPurchase(tokenPurchase, refundReasonCode, request.refundReasonText());
        tokenService.refundPaidTokenCount(memberId, refundTokenCount);

        log.info("토큰 환불 완료 - memberId: {}, tokenPurchaseId: {}, 차감된 토큰: {}", memberId, tokenPurchaseId, refundTokenCount);
    }

    @Transactional(readOnly = true)
    public TokenPurchaseResponses readMyTokenPurchases(Long memberId, TokenPurchaseState state, Pageable pageable) {
        Page<TokenPurchase> tokenPurchasePage = tokenPurchaseService.findTokenPurchasesByMemberId(memberId, state, pageable);
        List<TokenPurchaseResponse> tokenPurchases = tokenPurchasePage.stream()
                .map(TokenPurchaseResponse::from)
                .toList();
        
        long totalPageCount = tokenPurchasePage.getTotalPages();
        return TokenPurchaseResponses.from(tokenPurchases, totalPageCount);
    }
}
