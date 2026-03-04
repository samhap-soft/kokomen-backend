package com.samhap.kokomen.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.TosspaymentsClient;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.service.TokenFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentRecoveryService {

    private final TosspaymentsPaymentService tosspaymentsPaymentService;
    private final TosspaymentsClient tosspaymentsClient;
    private final TokenFacadeService tokenFacadeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processRecovery(String paymentKey) {
        TosspaymentsPayment payment = tosspaymentsPaymentService.readByPaymentKey(paymentKey);
        if (payment.isTerminal()) {
            log.info("복구 스킵 - 이미 종료 상태: paymentKey={}, state={}", paymentKey, payment.getState());
            return;
        }

        TosspaymentsPaymentResponse tossResponse;
        try {
            tossResponse = tosspaymentsClient.getPayment(paymentKey);
        } catch (Exception e) {
            log.warn("토스 결제 조회 실패 - paymentKey={}, 다음 주기에 재시도", paymentKey, e);
            return;
        }

        TosspaymentsStatus tossStatus = tossResponse.status();
        log.info("복구 진행 - paymentKey={}, 내부상태={}, 토스상태={}", paymentKey, payment.getState(), tossStatus);

        switch (tossStatus) {
            case DONE -> recoverAsDone(payment, tossResponse);
            case CANCELED, PARTIAL_CANCELED -> {
                payment.updateState(PaymentState.CANCELED);
                log.info("복구 완료(취소) - paymentKey={}", paymentKey);
            }
            case EXPIRED, ABORTED -> {
                payment.updateState(PaymentState.NOT_NEED_CANCEL);
                log.info("복구 완료(만료/중단) - paymentKey={}", paymentKey);
            }
            default -> log.info("복구 대기 - paymentKey={}, 토스상태={}", paymentKey, tossStatus);
        }
    }

    private void recoverAsDone(TosspaymentsPayment payment, TosspaymentsPaymentResponse tossResponse) {
        String paymentKey = payment.getPaymentKey();

        if (tokenFacadeService.existsByPaymentKey(paymentKey)) {
            payment.updateState(PaymentState.COMPLETED);
            log.info("복구 완료(토큰 이미 지급됨) - paymentKey={}", paymentKey);
            return;
        }

        PurchaseMetadata metadata = parseMetadata(payment.getMetadata());
        int tokenCount = metadata.count();

        payment.updateState(PaymentState.COMPLETED);

        TokenPurchase tokenPurchase = TokenPurchase.builder()
                .memberId(payment.getMemberId())
                .paymentKey(paymentKey)
                .orderId(payment.getOrderId())
                .totalAmount(payment.getTotalAmount())
                .orderName(payment.getOrderName())
                .productName(metadata.productName())
                .purchaseCount(tokenCount)
                .unitPrice(metadata.unitPrice())
                .paymentMethod(tossResponse.method())
                .easyPayProvider(tossResponse.easyPay() != null ? tossResponse.easyPay().provider() : null)
                .build();
        tokenFacadeService.grantPurchasedTokens(tokenPurchase, tokenCount);

        log.info("복구 완료(토큰 지급) - paymentKey={}, memberId={}, tokenCount={}",
                paymentKey, payment.getMemberId(), tokenCount);
    }

    private PurchaseMetadata parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, PurchaseMetadata.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("메타데이터 파싱 실패: " + metadataJson, e);
        }
    }
}
