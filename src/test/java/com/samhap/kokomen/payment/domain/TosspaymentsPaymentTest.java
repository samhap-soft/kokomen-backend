package com.samhap.kokomen.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.samhap.kokomen.global.exception.InternalServerErrorException;
import org.junit.jupiter.api.Test;

class TosspaymentsPaymentTest {

    @Test
    void 토스페이먼츠_응답_검증에_성공한다() {
        String paymentKey = "payment_key";
        String orderId = "order_id";
        long totalAmount = 10000L;
        TosspaymentsPayment payment = new TosspaymentsPayment(
                paymentKey, 1L, orderId, "주문명", totalAmount, "{}", ServiceType.INTERVIEW
        );

        assertDoesNotThrow(() -> payment.validateTosspaymentsResult(paymentKey, orderId, totalAmount));
    }

    @Test
    void paymentKey가_다르면_검증에_실패한다() {
        TosspaymentsPayment payment = new TosspaymentsPayment(
                "payment_key", 1L, "order_id", "주문명", 10000L, "{}", ServiceType.INTERVIEW
        );

        assertThatThrownBy(() -> payment.validateTosspaymentsResult("wrong_key", "order_id", 10000L))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentErrorMessage.PAYMENT_KEY_MISMATCH.getMessage());
    }

    @Test
    void orderId가_다르면_검증에_실패한다() {
        TosspaymentsPayment payment = new TosspaymentsPayment(
                "payment_key", 1L, "order_id", "주문명", 10000L, "{}", ServiceType.INTERVIEW
        );

        assertThatThrownBy(() -> payment.validateTosspaymentsResult("payment_key", "wrong_order", 10000L))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentErrorMessage.ORDER_ID_MISMATCH.getMessage());
    }

    @Test
    void totalAmount가_다르면_검증에_실패한다() {
        TosspaymentsPayment payment = new TosspaymentsPayment(
                "payment_key", 1L, "order_id", "주문명", 10000L, "{}", ServiceType.INTERVIEW
        );

        assertThatThrownBy(() -> payment.validateTosspaymentsResult("payment_key", "order_id", 99999L))
                .isInstanceOf(InternalServerErrorException.class)
                .hasMessage(PaymentErrorMessage.TOTAL_AMOUNT_MISMATCH.getMessage());
    }

    @Test
    void 결제_상태를_변경할_수_있다() {
        TosspaymentsPayment payment = new TosspaymentsPayment(
                "payment_key", 1L, "order_id", "주문명", 10000L, "{}", ServiceType.INTERVIEW
        );

        payment.updateState(PaymentState.APPROVED);

        assertThat(payment.getState()).isEqualTo(PaymentState.APPROVED);
    }
}
