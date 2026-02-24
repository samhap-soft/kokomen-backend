package com.samhap.kokomen.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentResultFixtureBuilder;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.PaymentType;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.payment.external.dto.TosspaymentsCancel;
import com.samhap.kokomen.payment.external.dto.TosspaymentsPaymentResponse;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentRepository;
import com.samhap.kokomen.payment.repository.TosspaymentsPaymentResultRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TosspaymentsTransactionServiceTest extends BaseTest {

    @Autowired
    private TosspaymentsTransactionService tosspaymentsTransactionService;

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Autowired
    private TosspaymentsPaymentResultRepository tosspaymentsPaymentResultRepository;

    @Test
    void 취소_결과에_취소_정보가_있으면_결제_결과에_취소_정보를_업데이트한다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPaymentResult result = TosspaymentsPaymentResultFixtureBuilder.builder()
                .tosspaymentsPayment(payment)
                .build();
        tosspaymentsPaymentResultRepository.save(result);

        LocalDateTime canceledAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        TosspaymentsCancel cancel = new TosspaymentsCancel(
                "cancel_tx_key", "단순 변심", 0L,
                canceledAt, 0L, null, 10000L, 0L, 10000L, "DONE", null
        );
        TosspaymentsPaymentResponse response = new TosspaymentsPaymentResponse(
                "payment_key", PaymentType.NORMAL, "order_id", "주문명",
                "tvivarepublica", "KRW", "카드", 10000L, 10000L,
                TosspaymentsStatus.CANCELED, LocalDateTime.now(), LocalDateTime.now(),
                "cancel_tx_key", 9091L, 909L, 0L, 0L, true,
                "{}", null, null, null, "KR", null, List.of(cancel)
        );

        tosspaymentsTransactionService.applyCancelResult(response);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key")
                .orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.CANCELED);
        TosspaymentsPaymentResult updatedResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(updatedPayment.getId()).orElseThrow();
        assertThat(updatedResult.getCancelReason()).isEqualTo("단순 변심");
        assertThat(updatedResult.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(updatedResult.getCancelStatus()).isEqualTo("DONE");
    }

    @Test
    void 취소_결과에_취소_정보가_없으면_결제_상태만_변경한다() {
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("payment_key")
                .build();
        payment.updateState(PaymentState.COMPLETED);
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPaymentResult result = TosspaymentsPaymentResultFixtureBuilder.builder()
                .tosspaymentsPayment(payment)
                .build();
        tosspaymentsPaymentResultRepository.save(result);

        TosspaymentsPaymentResponse response = new TosspaymentsPaymentResponse(
                "payment_key", PaymentType.NORMAL, "order_id", "주문명",
                "tvivarepublica", "KRW", "카드", 10000L, 10000L,
                TosspaymentsStatus.CANCELED, LocalDateTime.now(), LocalDateTime.now(),
                "tx_key", 9091L, 909L, 0L, 0L, true,
                "{}", null, null, null, "KR", null, null
        );

        tosspaymentsTransactionService.applyCancelResult(response);

        TosspaymentsPayment updatedPayment = tosspaymentsPaymentRepository.findByPaymentKey("payment_key")
                .orElseThrow();
        assertThat(updatedPayment.getState()).isEqualTo(PaymentState.CANCELED);
        TosspaymentsPaymentResult updatedResult = tosspaymentsPaymentResultRepository
                .findByTosspaymentsPaymentId(updatedPayment.getId()).orElseThrow();
        assertThat(updatedResult.getCancelReason()).isNull();
    }
}
