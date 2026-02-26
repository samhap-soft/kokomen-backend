package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData.WebhookEasyPay;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.PurchaseMetadata;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WebhookServiceTest extends BaseTest {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

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

        WebhookPayload payload = createWebhookPayload("cancel_test_key", "cancel_order_1", TosspaymentsStatus.CANCELED, 10000L, "카드", null);
        webhookService.handlePaymentStatusChanged(payload);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("cancel_test_key").orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.CANCELED);
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
