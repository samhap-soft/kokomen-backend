package com.samhap.kokomen.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.InternalServerErrorException;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.service.TokenFacadeService;
import com.samhap.kokomen.global.annotation.DistributedLock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class WebhookService {

    private final TosspaymentsPaymentService tosspaymentsPaymentService;
    private final TokenFacadeService tokenFacadeService;
    private final ObjectMapper objectMapper;

    @DistributedLock(prefix = "payment", key = "#payload.data().paymentKey()")
    @Transactional
    public void handlePaymentStatusChanged(WebhookPayload payload) {
        WebhookPaymentData data = payload.data();
        String paymentKey = data.paymentKey();
        TosspaymentsStatus webhookStatus = data.status();

        Optional<TosspaymentsPayment> findPayment = tosspaymentsPaymentService.findByPaymentKey(paymentKey);
        if (findPayment.isEmpty()) {
            log.info("웹훅 무시 - 등록되지 않은 paymentKey: {}", paymentKey);
            return;
        }
        TosspaymentsPayment payment = findPayment.get();
        if (payment.isTerminal()) {
            log.info("웹훅 스킵 - 종료 상태: paymentKey={}, state={}", paymentKey, payment.getState());
            return;
        }

        switch (webhookStatus) {
            case DONE -> handleDone(payment, data);
            case CANCELED, PARTIAL_CANCELED -> handleCanceled(payment, webhookStatus);
            case EXPIRED, ABORTED -> handleFailed(payment, webhookStatus);
            default -> log.info("웹훅 무시 - 처리 불필요 status: {}", webhookStatus);
        }
    }

    private void handleDone(TosspaymentsPayment payment, WebhookPaymentData data) {
        if (payment.canCompleteByWebhook()) {
            PurchaseMetadata metadata = parseMetadata(payment.getMetadata());
            Long memberId = payment.getMemberId();
            int tokenCount = metadata.count();

            payment.updateState(PaymentState.COMPLETED);

            TokenPurchase tokenPurchase = TokenPurchase.builder()
                    .memberId(memberId)
                    .paymentKey(payment.getPaymentKey())
                    .orderId(payment.getOrderId())
                    .totalAmount(payment.getTotalAmount())
                    .orderName(payment.getOrderName())
                    .productName(metadata.productName())
                    .purchaseCount(tokenCount)
                    .unitPrice(metadata.unitPrice())
                    .paymentMethod(data.method())
                    .easyPayProvider(data.easyPay() != null ? data.easyPay().provider() : null)
                    .build();
            tokenFacadeService.grantPurchasedTokens(tokenPurchase, tokenCount);

            log.info("웹훅으로 토큰 지급 완료 - memberId: {}, paymentKey: {}, tokenCount: {}",
                    memberId, payment.getPaymentKey(), tokenCount);
        }
    }

    private PurchaseMetadata parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, PurchaseMetadata.class);
        } catch (JsonProcessingException e) {
            log.error("메타데이터 파싱 실패: {}", metadataJson, e);
            throw new InternalServerErrorException("메타데이터 파싱에 실패했습니다.", e);
        }
    }

    private void handleCanceled(TosspaymentsPayment payment, TosspaymentsStatus status) {
        if (payment.canCancelByWebhook()) {
            payment.updateState(PaymentState.CANCELED);
            log.info("웹훅 취소 처리 - paymentKey: {}, status: {}", payment.getPaymentKey(), status);
        } else {
            log.info("웹훅 취소 무시 - 취소 불가 상태: paymentKey={}, state={}", payment.getPaymentKey(), payment.getState());
        }
    }

    private void handleFailed(TosspaymentsPayment payment, TosspaymentsStatus status) {
        if (payment.canResolveAsNotNeeded()) {
            payment.updateState(PaymentState.NOT_NEED_CANCEL);
            log.info("웹훅 만료/실패 처리 - paymentKey: {}, status: {}", payment.getPaymentKey(), status);
        } else {
            log.info("웹훅 만료/실패 무시 - 처리 불가 상태: paymentKey={}, state={}", payment.getPaymentKey(), payment.getState());
        }
    }
}
