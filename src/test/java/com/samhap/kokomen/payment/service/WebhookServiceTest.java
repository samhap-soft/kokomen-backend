package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResultFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.EasyPay;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData.WebhookEasyPay;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.dto.TokenPurchaseRequest;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WebhookServiceTest extends BaseTest {

    @Autowired
    private WebhookService webhookService;

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
    void 결제완료_웹훅_수신시_토큰이_지급된다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder().memberId(memberId).type(TokenType.PAID).tokenCount(0).build());

        String metadata = objectMapper.writeValueAsString(new PurchaseMetadata("TOKEN_10", 10, 100L));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("webhook_test_key")
                .memberId(memberId)
                .orderId("webhook_order_1")
                .totalAmount(1000L)
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        tosspaymentsPaymentRepository.save(payment);

        given(tosspaymentsClient.getPayment("webhook_test_key"))
                .willReturn(createTossResponse("webhook_test_key", "webhook_order_1", 1000L, TosspaymentsStatus.DONE, "카드", null));

        WebhookPayload payload = createWebhookPayload("webhook_test_key", "webhook_order_1", TosspaymentsStatus.DONE, 1000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("webhook_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.COMPLETED);

        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(10);

        assertThat(tokenPurchaseRepository.findAll()).hasSize(1);
        TokenPurchase tokenPurchase = tokenPurchaseRepository.findAll().get(0);
        assertThat(tokenPurchase.getMemberId()).isEqualTo(memberId);
        assertThat(tokenPurchase.getPurchaseCount()).isEqualTo(10);
        assertThat(tokenPurchase.getPaymentKey()).isEqualTo("webhook_test_key");
        assertThat(tokenPurchase.getPaymentMethod()).isEqualTo("카드");
    }

    @Test
    void CONNECTION_TIMEOUT_상태에서_결제완료_웹훅_수신시_토큰이_지급된다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder().memberId(memberId).type(TokenType.PAID).tokenCount(0).build());

        String metadata = objectMapper.writeValueAsString(new PurchaseMetadata("TOKEN_20", 20, 100L));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("timeout_test_key")
                .memberId(memberId)
                .orderId("timeout_order_1")
                .totalAmount(2000L)
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.CONNECTION_TIMEOUT);
        tosspaymentsPaymentRepository.save(payment);

        given(tosspaymentsClient.getPayment("timeout_test_key"))
                .willReturn(createTossResponse("timeout_test_key", "timeout_order_1", 2000L, TosspaymentsStatus.DONE, "카드", null));

        WebhookPayload payload = createWebhookPayload("timeout_test_key", "timeout_order_1", TosspaymentsStatus.DONE, 2000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("timeout_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.COMPLETED);

        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(20);
    }

    @Test
    void NEED_APPROVE_상태에서_결제완료_웹훅_수신시_토큰이_지급된다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder().memberId(memberId).type(TokenType.PAID).tokenCount(0).build());

        String metadata = objectMapper.writeValueAsString(new PurchaseMetadata("TOKEN_10", 10, 100L));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("approve_test_key")
                .memberId(memberId)
                .orderId("approve_order_1")
                .totalAmount(1000L)
                .metadata(metadata)
                .build();
        tosspaymentsPaymentRepository.save(payment);

        given(tosspaymentsClient.getPayment("approve_test_key"))
                .willReturn(createTossResponse("approve_test_key", "approve_order_1", 1000L, TosspaymentsStatus.DONE, "간편결제",
                        new EasyPay("토스페이", 1000L, 0L)));

        WebhookPayload payload = createWebhookPayload("approve_test_key", "approve_order_1", TosspaymentsStatus.DONE, 1000L, "간편결제",
                new WebhookEasyPay("토스페이", 1000L, 0L));
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("approve_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.COMPLETED);

        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(10);

        TokenPurchase tokenPurchase = tokenPurchaseRepository.findAll().get(0);
        assertThat(tokenPurchase.getPaymentMethod()).isEqualTo("간편결제");
        assertThat(tokenPurchase.getEasyPayProvider()).isEqualTo("토스페이");
    }

    @Test
    void 이미_완료된_결제에_웹훅이_오면_무시한다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder().memberId(memberId).type(TokenType.PAID).tokenCount(5).build());

        String metadata = objectMapper.writeValueAsString(new PurchaseMetadata("TOKEN_10", 10, 100L));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("completed_test_key")
                .memberId(memberId)
                .orderId("completed_order_1")
                .totalAmount(1000L)
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        WebhookPayload payload = createWebhookPayload("completed_test_key", "completed_order_1", TosspaymentsStatus.DONE, 1000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(5);

        assertThat(tokenPurchaseRepository.findAll()).isEmpty();
    }

    @Test
    void 등록되지_않은_paymentKey_웹훅이_오면_무시한다() {
        WebhookPayload payload = createWebhookPayload("unknown_key", "unknown_order", TosspaymentsStatus.DONE, 1000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        assertThat(tokenPurchaseRepository.findAll()).isEmpty();
    }

    @Test
    void 만료된_결제에_웹훅이_오면_상태를_정리한다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("expired_test_key")
                .orderId("expired_order_1")
                .build();
        tosspaymentsPaymentRepository.save(payment);

        given(tosspaymentsClient.getPayment("expired_test_key"))
                .willReturn(createTossResponse("expired_test_key", "expired_order_1", 10000L, TosspaymentsStatus.EXPIRED, "카드", null));

        WebhookPayload payload = createWebhookPayload("expired_test_key", "expired_order_1", TosspaymentsStatus.EXPIRED, 10000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("expired_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.NOT_NEED_CANCEL);
    }

    @Test
    void 취소_웹훅이_오면_CANCELED_상태로_변경한다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("cancel_test_key")
                .orderId("cancel_order_1")
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        tosspaymentsPaymentRepository.save(payment);

        given(tosspaymentsClient.getPayment("cancel_test_key"))
                .willReturn(createTossResponse("cancel_test_key", "cancel_order_1", 10000L, TosspaymentsStatus.CANCELED, "카드", null));

        WebhookPayload payload = createWebhookPayload("cancel_test_key", "cancel_order_1", TosspaymentsStatus.CANCELED, 10000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("cancel_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.CANCELED);
    }

    @Test
    void 결제완료_웹훅으로_완료된_결제에는_결제_결과가_저장된다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(memberId)
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());
        TosspaymentsPayment payment = saveNeedCancelPayment(memberId, "result_test_key", "result_order_1");
        long totalAmount = TokenProduct.TOKEN_10.getPrice();

        TosspaymentsPaymentResponse tossResponse = createTossResponse(
                "result_test_key", "result_order_1", totalAmount, TosspaymentsStatus.DONE, "카드", null);
        given(tosspaymentsClient.getPayment("result_test_key")).willReturn(tossResponse);

        webhookService.handlePaymentStatusChanged(createWebhookPayload(
                "result_test_key", "result_order_1", TosspaymentsStatus.DONE, totalAmount, "카드", null));

        Optional<TosspaymentsPaymentResult> savedResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(payment.getId());
        assertThat(savedResult).isPresent();
        assertThat(savedResult.get().getTosspaymentsStatus()).isEqualTo(TosspaymentsStatus.DONE);
        assertThat(savedResult.get().getMethod()).isEqualTo("카드");
        assertThat(savedResult.get().getTotalAmount()).isEqualTo(totalAmount);
        assertThat(savedResult.get().getApprovedAt()).isEqualTo(tossResponse.approvedAt());
    }

    @Test
    void 결제완료_웹훅으로_완료된_결제를_환불하면_결제가_취소되고_결제_결과에_취소_정보가_반영된다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(memberId)
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());
        TosspaymentsPayment payment = saveNeedCancelPayment(memberId, "refund_test_key", "refund_order_1");
        long totalAmount = TokenProduct.TOKEN_10.getPrice();

        given(tosspaymentsClient.getPayment("refund_test_key")).willReturn(createTossResponse(
                "refund_test_key", "refund_order_1", totalAmount, TosspaymentsStatus.DONE, "카드", null));
        webhookService.handlePaymentStatusChanged(createWebhookPayload(
                "refund_test_key", "refund_order_1", TosspaymentsStatus.DONE, totalAmount, "카드", null));

        LocalDateTime canceledAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        given(tosspaymentsClient.cancelPayment(eq("refund_test_key"), any(), any()))
                .willReturn(createCancelResponse("refund_test_key", "refund_order_1", totalAmount, canceledAt));
        TokenPurchase tokenPurchase = tokenPurchaseRepository.findAll().get(0);
        TokenRefundRequest refundRequest = new TokenRefundRequest(RefundReasonCode.CHANGE_OF_MIND, null);

        assertThatCode(() -> tokenFacadeService.refundTokens(memberId, tokenPurchase.getId(), refundRequest))
                .doesNotThrowAnyException();

        TosspaymentsPayment canceledPayment = tosspaymentsPaymentRepository.findByPaymentKey("refund_test_key")
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

    @Test
    void 결제완료_웹훅으로_완료된_결제를_같은_paymentKey로_다시_구매_요청해도_토큰은_한_번만_지급된다()
            throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(memberId)
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());
        saveNeedCancelPayment(memberId, "repurchase_test_key", "repurchase_order_1");
        TokenProduct product = TokenProduct.TOKEN_10;

        given(tosspaymentsClient.getPayment("repurchase_test_key")).willReturn(createTossResponse(
                "repurchase_test_key", "repurchase_order_1", product.getPrice(), TosspaymentsStatus.DONE, "카드", null));
        webhookService.handlePaymentStatusChanged(createWebhookPayload(
                "repurchase_test_key", "repurchase_order_1", TosspaymentsStatus.DONE, product.getPrice(), "카드", null));

        TokenPurchaseRequest purchaseRequest = new TokenPurchaseRequest("repurchase_test_key", "repurchase_order_1",
                product.getPrice(), product.getOrderName(), product.name());

        assertThatCode(() -> tokenFacadeService.purchaseTokens(memberId, purchaseRequest))
                .doesNotThrowAnyException();

        Token paidToken = tokenRepository.findByMemberIdAndType(memberId, TokenType.PAID).orElseThrow();
        assertThat(paidToken.getTokenCount()).isEqualTo(product.getTokenCount());
        assertThat(tokenPurchaseRepository.findAll()).hasSize(1);
        verify(tosspaymentsClient, never()).confirmPayment(any(), any());
    }

    @Test
    void 결제_결과가_이미_있는_결제에_결제완료_웹훅이_오면_결제_결과를_중복_저장하지_않는다() throws JsonProcessingException {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long memberId = member.getId();
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(memberId)
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());
        TokenProduct product = TokenProduct.TOKEN_10;
        String metadata = objectMapper.writeValueAsString(
                new PurchaseMetadata(product.name(), product.getTokenCount(), product.getUnitPrice()));
        // 승인 경로가 결과 행까지 저장했지만 토큰 지급 전에 멈춘 결제
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("approved_test_key")
                .memberId(memberId)
                .orderId("approved_order_1")
                .totalAmount(product.getPrice())
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.APPROVED);
        tosspaymentsPaymentRepository.save(payment);
        TosspaymentsPaymentResult approvedResult = tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder().tosspaymentsPayment(payment).build());

        given(tosspaymentsClient.getPayment("approved_test_key")).willReturn(createTossResponse(
                "approved_test_key", "approved_order_1", product.getPrice(), TosspaymentsStatus.DONE, "카드", null));
        webhookService.handlePaymentStatusChanged(createWebhookPayload(
                "approved_test_key", "approved_order_1", TosspaymentsStatus.DONE, product.getPrice(), "카드", null));

        TosspaymentsPayment completedPayment = tosspaymentsPaymentRepository.findByPaymentKey("approved_test_key")
                .orElseThrow();
        assertThat(completedPayment.getState()).isEqualTo(PaymentState.COMPLETED);
        assertThat(tosspaymentsPaymentResultRepository.findAll())
                .extracting(TosspaymentsPaymentResult::getId)
                .containsExactly(approvedResult.getId());
    }

    // 승인 요청이 읽기 타임아웃으로 끝나 결과 행 없이 NEED_CANCEL로 남은 결제
    private TosspaymentsPayment saveNeedCancelPayment(Long memberId, String paymentKey, String orderId)
            throws JsonProcessingException {
        TokenProduct product = TokenProduct.TOKEN_10;
        String metadata = objectMapper.writeValueAsString(
                new PurchaseMetadata(product.name(), product.getTokenCount(), product.getUnitPrice()));
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .memberId(memberId)
                .orderId(orderId)
                .orderName(product.getOrderName())
                .totalAmount(product.getPrice())
                .metadata(metadata)
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        return tosspaymentsPaymentRepository.save(payment);
    }

    private TosspaymentsPaymentResponse createTossResponse(String paymentKey, String orderId, Long totalAmount,
                                                            TosspaymentsStatus status, String method, EasyPay easyPay) {
        return TosspaymentsPaymentResponseFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .status(status)
                .method(method)
                .easyPay(easyPay)
                .build();
    }

    private TosspaymentsPaymentResponse createCancelResponse(String paymentKey, String orderId, Long totalAmount,
                                                             LocalDateTime canceledAt) {
        TosspaymentsCancel cancel = new TosspaymentsCancel(
                "cancel_tx_key", "단순 변심", 0L,
                canceledAt, 0L, null, totalAmount, 0L, 0L, "DONE", null
        );
        return TosspaymentsPaymentResponseFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .balanceAmount(0L)
                .status(TosspaymentsStatus.CANCELED)
                .cancels(List.of(cancel))
                .build();
    }

    private WebhookPayload createWebhookPayload(String paymentKey, String orderId, TosspaymentsStatus status,
                                                  Long totalAmount, String method, WebhookEasyPay easyPay) {
        WebhookPaymentData data = new WebhookPaymentData(
                paymentKey, orderId, null, null, null, method,
                totalAmount, null, status,
                LocalDateTime.now(), null, null,
                null, null, null, null,
                false, easyPay, null
        );
        return new WebhookPayload("PAYMENT_STATUS_CHANGED", "2026-02-26T00:00:00.000000", data);
    }
}
