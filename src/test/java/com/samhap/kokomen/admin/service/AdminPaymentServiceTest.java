package com.samhap.kokomen.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhap.kokomen.admin.service.dto.AdminCancelPaymentRequest;
import com.samhap.kokomen.admin.service.dto.AdminPaymentPageResponse;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResultFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Admin;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.AdminRepository;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class AdminPaymentServiceTest extends BaseTest {

    @Autowired
    private AdminPaymentService adminPaymentService;

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Autowired
    private TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;

    @Test
    void 전체_결제목록을_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        TosspaymentsPayment payment1 = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_1")
                        .orderId("order_1")
                        .memberId(1L)
                        .build()
        );
        TosspaymentsPayment payment2 = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_2")
                        .orderId("order_2")
                        .memberId(2L)
                        .build()
        );

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        AdminPaymentPageResponse response = adminPaymentService.findPayments(null, null, null, null, pageable, new MemberAuth(member.getId()));

        // then
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.data()).hasSize(2);
    }

    @Test
    void memberId로_결제목록을_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        Long targetMemberId = 100L;
        tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_1")
                        .orderId("order_1")
                        .memberId(targetMemberId)
                        .build()
        );
        tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_2")
                        .orderId("order_2")
                        .memberId(200L)
                        .build()
        );

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        AdminPaymentPageResponse response = adminPaymentService.findPayments(
                targetMemberId, null, null, null, pageable, new MemberAuth(member.getId())
        );

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).memberId()).isEqualTo(targetMemberId);
    }

    @Test
    void state로_필터링하여_결제목록을_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        TosspaymentsPayment completedPayment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_1")
                        .orderId("order_1")
                        .build()
        );
        completedPayment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(completedPayment);

        TosspaymentsPayment canceledPayment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_2")
                        .orderId("order_2")
                        .build()
        );
        canceledPayment.updateState(PaymentState.CANCELED);
        tosspaymentsPaymentRepository.save(canceledPayment);

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        AdminPaymentPageResponse response = adminPaymentService.findPayments(
                null, PaymentState.COMPLETED, null, null, pageable, new MemberAuth(member.getId())
        );

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).state()).isEqualTo(PaymentState.COMPLETED);
    }

    @Test
    void 결제목록_조회시_결제결과도_함께_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_1")
                        .orderId("order_1")
                        .build()
        );
        TosspaymentsPaymentResult result = tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .method("카드")
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        AdminPaymentPageResponse response = adminPaymentService.findPayments(null, null, null, null, pageable, new MemberAuth(member.getId()));

        // then
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).result()).isNotNull();
        assertThat(response.data().get(0).result().method()).isEqualTo("카드");
        assertThat(response.data().get(0).result().tosspaymentsStatus()).isEqualTo(TosspaymentsStatus.DONE);
    }

    @Test
    void 결제를_취소한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        Member buyer = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(buyer.getId())
                .type(TokenType.PAID)
                .tokenCount(10)
                .build());
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                .memberId(buyer.getId())
                .paymentKey("payment_key_cancel")
                .orderId("order_cancel")
                .count(10)
                .remainingCount(10)
                .state(TokenPurchaseState.REFUNDABLE)
                .build());
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_cancel")
                        .orderId("order_cancel")
                        .memberId(buyer.getId())
                        .build()
        );
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPaymentResult result = tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );

        TosspaymentsPaymentResponse cancelResponse = createCancelResponse(payment);
        when(tosspaymentsClient.cancelPayment(
                argThat(key -> key.equals("payment_key_cancel")),
                argThat(req -> req.cancelReason().equals("테스트 환불")),
                argThat(key -> key != null)
        )).thenReturn(cancelResponse);

        AdminCancelPaymentRequest request = new AdminCancelPaymentRequest("테스트 환불");

        // when
        adminPaymentService.cancelPayment(payment.getId(), request, new MemberAuth(member.getId()));

        // then
        TosspaymentsPayment canceledPayment = tosspaymentsPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(canceledPayment.getState()).isEqualTo(PaymentState.CANCELED);
        TokenPurchase refundedTokenPurchase = tokenPurchaseRepository.findById(tokenPurchase.getId()).orElseThrow();
        assertThat(refundedTokenPurchase.getState()).isEqualTo(TokenPurchaseState.REFUNDED);
        assertThat(refundedTokenPurchase.getRemainingCount()).isZero();
        assertThat(refundedTokenPurchase.getRefundReasonCode()).isEqualTo(RefundReasonCode.OTHER);
        assertThat(refundedTokenPurchase.getRefundReasonText()).isEqualTo("[관리자 취소] 테스트 환불");
        assertThat(tokenRepository.findByMemberIdAndType(buyer.getId(), TokenType.PAID).orElseThrow().getTokenCount())
                .isZero();
    }

    @Test
    void 토큰을_일부_사용한_구매의_결제는_취소할_수_없다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        Member buyer = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(buyer.getId())
                .type(TokenType.PAID)
                .tokenCount(5)
                .build());
        TokenPurchase tokenPurchase = tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                .memberId(buyer.getId())
                .paymentKey("payment_key_used")
                .orderId("order_used")
                .count(10)
                .remainingCount(5)
                .state(TokenPurchaseState.USABLE)
                .build());
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_used")
                        .orderId("order_used")
                        .memberId(buyer.getId())
                        .build()
        );
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );
        when(tosspaymentsClient.cancelPayment(any(), any(), any())).thenReturn(createCancelResponse(payment));

        AdminCancelPaymentRequest request = new AdminCancelPaymentRequest("테스트 환불");

        // when & then
        assertThatThrownBy(() -> adminPaymentService.cancelPayment(payment.getId(), request,
                new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("환불 불가능한 상태입니다. 환불 가능한 토큰은 사용하지 않은 상태여야 합니다.");
        verify(tosspaymentsClient, never()).cancelPayment(any(), any(), any());
        assertThat(tosspaymentsPaymentRepository.findById(payment.getId()).orElseThrow().getState())
                .isEqualTo(PaymentState.COMPLETED);
        TokenPurchase notRefundedTokenPurchase = tokenPurchaseRepository.findById(tokenPurchase.getId())
                .orElseThrow();
        assertThat(notRefundedTokenPurchase.getState()).isEqualTo(TokenPurchaseState.USABLE);
        assertThat(notRefundedTokenPurchase.getRemainingCount()).isEqualTo(5);
        assertThat(tokenRepository.findByMemberIdAndType(buyer.getId(), TokenType.PAID).orElseThrow().getTokenCount())
                .isEqualTo(5);
    }

    @Test
    void 토큰이_지급되지_않은_결제는_결제만_취소한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        Member buyer = memberRepository.save(MemberFixtureBuilder.builder().build());
        TosspaymentsPayment payment = tosspaymentsPaymentRepository.save(
                TosspaymentsPaymentFixtureBuilder.builder()
                        .paymentKey("payment_key_approved")
                        .orderId("order_approved")
                        .memberId(buyer.getId())
                        .build()
        );
        payment.updateState(PaymentState.APPROVED);
        tosspaymentsPaymentRepository.save(payment);

        tosspaymentsPaymentResultRepository.save(
                TosspaymentsPaymentResultFixtureBuilder.builder()
                        .tosspaymentsPayment(payment)
                        .tosspaymentsStatus(TosspaymentsStatus.DONE)
                        .build()
        );
        when(tosspaymentsClient.cancelPayment(any(), any(), any())).thenReturn(createCancelResponse(payment));

        AdminCancelPaymentRequest request = new AdminCancelPaymentRequest("테스트 환불");

        // when
        adminPaymentService.cancelPayment(payment.getId(), request, new MemberAuth(member.getId()));

        // then
        assertThat(tosspaymentsPaymentRepository.findById(payment.getId()).orElseThrow().getState())
                .isEqualTo(PaymentState.CANCELED);
        verify(tosspaymentsClient).cancelPayment(eq("payment_key_approved"),
                argThat(cancelRequest -> cancelRequest.cancelReason().equals("테스트 환불")), any());
    }

    @Test
    void 존재하지_않는_결제를_취소시도하면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        adminRepository.save(new Admin(member));
        Long nonExistentPaymentId = 999999L;
        AdminCancelPaymentRequest request = new AdminCancelPaymentRequest("환불 사유");

        // when & then
        assertThatThrownBy(() -> adminPaymentService.cancelPayment(nonExistentPaymentId, request, new MemberAuth(member.getId())))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 결제입니다");
    }

    private TosspaymentsPaymentResponse createCancelResponse(TosspaymentsPayment payment) {
        return new TosspaymentsPaymentResponse(
                payment.getPaymentKey(),
                PaymentType.NORMAL,
                payment.getOrderId(),
                payment.getOrderName(),
                "mId",
                "KRW",
                "카드",
                payment.getTotalAmount(),
                0L,
                TosspaymentsStatus.CANCELED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                "transaction_key",
                9091L,
                909L,
                0L,
                0L,
                true,
                null,
                null,
                null,
                null,
                "KR",
                null,
                Collections.singletonList(
                        new TosspaymentsCancel(
                                "cancel_transaction_key",
                                "테스트 환불",
                                0L,
                                LocalDateTime.now(),
                                0L,
                                "receipt_key",
                                payment.getTotalAmount(),
                                0L,
                                0L,
                                "DONE",
                                null
                        )
                )
        );
    }
}
