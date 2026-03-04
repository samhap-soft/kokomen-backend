package com.samhap.kokomen.payment.service;

import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private static final List<PaymentState> RECOVERY_TARGET_STATES = List.of(
            PaymentState.NEED_CANCEL, PaymentState.APPROVED
    );
    private static final int STALE_THRESHOLD_MINUTES = 10;
    private static final int MAX_RECOVERY_COUNT = 10;
    private static final String LOCK_KEY_PREFIX = "lock:payment:";

    private final TosspaymentsPaymentService tosspaymentsPaymentService;
    private final PaymentRecoveryService paymentRecoveryService;
    private final RedissonClient redissonClient;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void recoverStalePayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<TosspaymentsPayment> stalePayments = tosspaymentsPaymentService.findStalePayments(
                RECOVERY_TARGET_STATES, threshold, MAX_RECOVERY_COUNT);

        if (stalePayments.isEmpty()) {
            return;
        }

        log.info("결제 복구 스케줄러 시작 - 대상 {}건", stalePayments.size());

        for (TosspaymentsPayment payment : stalePayments) {
            recoverPayment(payment);
        }
    }

    private void recoverPayment(TosspaymentsPayment payment) {
        String paymentKey = payment.getPaymentKey();
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + paymentKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("복구 스킵 - 락 획득 실패: paymentKey={}", paymentKey);
                return;
            }
            paymentRecoveryService.processRecovery(paymentKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("복구 중단 - 인터럽트: paymentKey={}", paymentKey);
        } catch (Exception e) {
            log.error("복구 실패 - paymentKey={}", paymentKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
