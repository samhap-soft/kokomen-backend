package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentRecoveryServiceTest extends BaseTest {

    private static final TokenProduct PRODUCT = TokenProduct.TOKEN_10;

    @Autowired
    private PaymentRecoveryService paymentRecoveryService;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;
    @Autowired
    private TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;
    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 토스에서_DONE으로_확인되어_복구된_결제에는_결제_결과가_저장된다() throws JsonProcessingException {
        Long memberId = saveMemberWithPaidToken();
        TosspaymentsPayment payment = saveNeedCancelPayment(memberId, "recovery_result_key", "recovery_result_order");
        TosspaymentsPaymentResponse tossResponse = createDoneResponse("recovery_result_key", "recovery_result_order");
        given(tosspaymentsClient.getPayment("recovery_result_key")).willReturn(tossResponse);

        paymentRecoveryService.processRecovery("recovery_result_key");

        TosspaymentsPayment recoveredPayment = tosspaymentsPaymentRepository.findByPaymentKey("recovery_result_key")
                .orElseThrow();
        assertThat(recoveredPayment.getState()).isEqualTo(PaymentState.COMPLETED);
        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(PRODUCT.getTokenCount());
        Optional<TosspaymentsPaymentResult> savedResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(payment.getId());
        assertThat(savedResult).isPresent();
        assertThat(savedResult.get().getTosspaymentsStatus()).isEqualTo(TosspaymentsStatus.DONE);
        assertThat(savedResult.get().getTotalAmount()).isEqualTo(PRODUCT.getPrice());
        assertThat(savedResult.get().getApprovedAt()).isEqualTo(tossResponse.approvedAt());
    }

    @Test
    void 토스에서_DONE으로_확인되어_복구된_결제를_환불하면_결제가_취소되고_결제_결과에_취소_정보가_반영된다()
            throws JsonProcessingException {
        Long memberId = saveMemberWithPaidToken();
        TosspaymentsPayment payment = saveNeedCancelPayment(memberId, "recovery_refund_key", "recovery_refund_order");
        given(tosspaymentsClient.getPayment("recovery_refund_key"))
                .willReturn(createDoneResponse("recovery_refund_key", "recovery_refund_order"));
        paymentRecoveryService.processRecovery("recovery_refund_key");

        LocalDateTime canceledAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        given(tosspaymentsClient.cancelPayment(eq("recovery_refund_key"), any(), any()))
                .willReturn(createCancelResponse("recovery_refund_key", "recovery_refund_order", canceledAt));
        TokenPurchase tokenPurchase = tokenPurchaseRepository.findAll().get(0);
        TokenRefundRequest refundRequest = new TokenRefundRequest(RefundReasonCode.CHANGE_OF_MIND, null);

        assertThatCode(() -> tokenFacadeService.refundTokens(memberId, tokenPurchase.getId(), refundRequest))
                .doesNotThrowAnyException();

        TosspaymentsPayment canceledPayment = tosspaymentsPaymentRepository.findByPaymentKey("recovery_refund_key")
                .orElseThrow();
        assertThat(canceledPayment.getState()).isEqualTo(PaymentState.CANCELED);
        TosspaymentsPaymentResult canceledResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(payment.getId()).orElseThrow();
        assertThat(canceledResult.getTosspaymentsStatus()).isEqualTo(TosspaymentsStatus.CANCELED);
        assertThat(canceledResult.getCancelReason()).isEqualTo("단순 변심");
        assertThat(canceledResult.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(canceledResult.getCancelStatus()).isEqualTo("DONE");
        TokenPurchase refundedPurchase = tokenPurchaseRepository.findById(tokenPurchase.getId()).orElseThrow();
        assertThat(refundedPurchase.getState()).isEqualTo(TokenPurchaseState.REFUNDED);
        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isZero();
    }

    private Long saveMemberWithPaidToken() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());
        return member.getId();
    }

    // 승인 요청이 읽기 타임아웃으로 끝나 결과 행 없이 NEED_CANCEL로 남은 결제
    private TosspaymentsPayment saveNeedCancelPayment(Long memberId, String paymentKey, String orderId)
            throws JsonProcessingException {
        String metadata = objectMapper.writeValueAsString(
                new PurchaseMetadata(PRODUCT.name(), PRODUCT.getTokenCount(), PRODUCT.getUnitPrice()));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .memberId(memberId)
                .orderId(orderId)
                .orderName(PRODUCT.getOrderName())
                .totalAmount(PRODUCT.getPrice())
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        return tosspaymentsPaymentRepository.save(payment);
    }

    private TosspaymentsPaymentResponse createDoneResponse(String paymentKey, String orderId) {
        return TosspaymentsPaymentResponseFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(PRODUCT.getPrice())
                .status(TosspaymentsStatus.DONE)
                .build();
    }

    private TosspaymentsPaymentResponse createCancelResponse(String paymentKey, String orderId,
                                                             LocalDateTime canceledAt) {
        TosspaymentsCancel cancel = new TosspaymentsCancel(
                "cancel_tx_key", "단순 변심", 0L,
                canceledAt, 0L, null, PRODUCT.getPrice(), 0L, 0L, "DONE", null
        );
        return TosspaymentsPaymentResponseFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(PRODUCT.getPrice())
                .balanceAmount(0L)
                .status(TosspaymentsStatus.CANCELED)
                .cancels(List.of(cancel))
                .build();
    }
}
