package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResponseFixtureBuilder;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.service.dto.WebhookPayload;
import com.samhap.kokomen.payment.service.dto.WebhookPaymentData;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentRecoverySchedulerTest extends BaseTest {

    private static final long AWAIT_TIMEOUT_SECONDS = 10L;

    @Autowired
    private PaymentRecoveryScheduler paymentRecoveryScheduler;
    @Autowired
    private WebhookService webhookService;
    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 웹훅은 {@code @DistributedLock}이 조립한 키로, 복구 스케줄러는 같은 RLock을 직접 잡는다.
     * 락 키 문자열을 테스트에 적지 않고 실제 웹훅 메서드가 락을 쥐게 하므로, 웹훅 쪽 키나 스케줄러 쪽 키 형식만
     * 바뀌어도 스케줄러가 락을 얻어 토스를 다시 조회하고 이 테스트가 실패한다. 승인·취소는 웹훅과 같은
     * {@code PaymentFacadeService.PAYMENT_LOCK_PREFIX}를 쓰는 것으로 맞추며, 이 테스트가 직접 잡지는 않는다.
     *
     * <p>스케줄러 빈은 컨텍스트가 뜰 때 백그라운드에서도 한 번 돈다. 그 실행이 이전 실행의 잔여 행을 집어
     * 같은 목을 호출해도 검증에 섞이지 않도록 결제 키를 실행마다 새로 만들고, 마지막에는 결제를 종료 상태로
     * 끝내 오래된 복구 대상을 남기지 않는다.
     */
    @Test
    void 결제_락을_쥔_웹훅이_처리_중인_결제는_복구_스케줄러가_토스_재조회_없이_건너뛴다() throws Exception {
        // given
        String paymentKey = "lock_test_key_" + UUID.randomUUID();
        String orderId = "lock_order_" + UUID.randomUUID();
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .build();
        payment.updateState(PaymentState.NEED_CANCEL);
        tosspaymentsPaymentRepository.save(payment);

        // 웹훅의 토스 조회를 붙잡아 웹훅이 결제 락을 쥔 채 머물게 한다. 그다음 조회는 만료로 바로 응답한다.
        CountDownLatch webhookHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseWebhook = new CountDownLatch(1);
        given(tosspaymentsClient.getPayment(paymentKey))
                .willAnswer(invocation -> {
                    webhookHoldsLock.countDown();
                    releaseWebhook.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return createTossResponse(paymentKey, orderId, TosspaymentsStatus.IN_PROGRESS);
                })
                .willReturn(createTossResponse(paymentKey, orderId, TosspaymentsStatus.EXPIRED));

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> webhook = executorService.submit(
                () -> webhookService.handlePaymentStatusChanged(createWebhookPayload(paymentKey, orderId)));
        try {
            assertThat(webhookHoldsLock.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            // 락을 쥔 뒤에 복구 대상(10분 넘게 갱신 없음)으로 만든다
            backdateUpdatedAtMinutes(payment.getId(), 11);

            // when
            paymentRecoveryScheduler.recoverStalePayments();

            // then — 락이 잡혀 있는 동안 토스 조회는 웹훅의 1회뿐이다
            verify(tosspaymentsClient, times(1)).getPayment(paymentKey);
        } finally {
            releaseWebhook.countDown();
            webhook.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executorService.shutdown();
        }

        // 락이 풀린 뒤에는 같은 결제를 복구한다. 앞에서 건너뛴 이유가 락이었음을 확인한다.
        paymentRecoveryScheduler.recoverStalePayments();

        verify(tosspaymentsClient, times(2)).getPayment(paymentKey);
        TosspaymentsPayment recoveredPayment = tosspaymentsPaymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow();
        assertThat(recoveredPayment.getState()).isEqualTo(PaymentState.NOT_NEED_CANCEL);
    }

    private void backdateUpdatedAtMinutes(Long paymentId, int minutes) {
        jdbcTemplate.update("UPDATE tosspayments_payment SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutes), paymentId);
    }

    private TosspaymentsPaymentResponse createTossResponse(String paymentKey, String orderId,
                                                           TosspaymentsStatus status) {
        return TosspaymentsPaymentResponseFixtureBuilder.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .status(status)
                .build();
    }

    private WebhookPayload createWebhookPayload(String paymentKey, String orderId) {
        WebhookPaymentData data = new WebhookPaymentData(
                paymentKey, orderId, null, null, null, "카드",
                10000L, null, TosspaymentsStatus.IN_PROGRESS,
                LocalDateTime.now(), null, null,
                null, null, null, null,
                false, null, null
        );
        return new WebhookPayload("PAYMENT_STATUS_CHANGED", "2026-02-26T00:00:00.000000", data);
    }
}
