package com.samhap.kokomen.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TosspaymentsPaymentResultTest {

    @Test
    void 취소_정보를_업데이트할_수_있다() {
        TosspaymentsPayment payment = new TosspaymentsPayment(
                "payment_key", 1L, "order_id", "주문명", 10000L, "{}", ServiceType.INTERVIEW
        );
        TosspaymentsPaymentResult result = new TosspaymentsPaymentResult(
                payment, PaymentType.NORMAL, "tvivarepublica", "KRW", 10000L, "카드",
                10000L, TosspaymentsStatus.DONE, LocalDateTime.now(), LocalDateTime.now(),
                "transaction_key", 9091L, 909L, 0L, 0L, true,
                null, null, null, null, "KR", null, null
        );

        String cancelReason = "단순 변심";
        LocalDateTime canceledAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        Long easyPayDiscountAmount = 0L;
        String lastTransactionKey = "cancel_transaction_key";
        String cancelStatus = "DONE";
        TosspaymentsStatus tosspaymentsStatus = TosspaymentsStatus.CANCELED;

        result.updateCancelInfo(cancelReason, canceledAt, easyPayDiscountAmount,
                lastTransactionKey, cancelStatus, tosspaymentsStatus);

        assertAll(
                () -> assertThat(result.getCancelReason()).isEqualTo(cancelReason),
                () -> assertThat(result.getCanceledAt()).isEqualTo(canceledAt),
                () -> assertThat(result.getEasyPayDiscountAmount()).isEqualTo(easyPayDiscountAmount),
                () -> assertThat(result.getLastTransactionKey()).isEqualTo(lastTransactionKey),
                () -> assertThat(result.getCancelStatus()).isEqualTo(cancelStatus),
                () -> assertThat(result.getTosspaymentsStatus()).isEqualTo(tosspaymentsStatus)
        );
    }
}
