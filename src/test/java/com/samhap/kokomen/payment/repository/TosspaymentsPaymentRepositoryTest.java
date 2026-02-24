package com.samhap.kokomen.payment.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.payment.TosspaymentsPaymentFixtureBuilder;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TosspaymentsPaymentRepositoryTest extends BaseTest {

    @Autowired
    private TosspaymentsPaymentRepository tosspaymentsPaymentRepository;

    @Test
    void 중복된_paymentKey로_저장하면_예외가_발생한다() {
        String duplicateKey = "duplicate_key";
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(duplicateKey)
                .orderId("order_1")
                .build();
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPayment duplicatePayment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey(duplicateKey)
                .orderId("order_2")
                .build();

        assertThatThrownBy(() -> tosspaymentsPaymentRepository.save(duplicatePayment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 중복된_orderId로_저장하면_예외가_발생한다() {
        String duplicateOrder = "duplicate_order";
        TosspaymentsPayment payment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("key_1")
                .orderId(duplicateOrder)
                .build();
        tosspaymentsPaymentRepository.save(payment);

        TosspaymentsPayment duplicatePayment = TosspaymentsPaymentFixtureBuilder.builder()
                .paymentKey("key_2")
                .orderId(duplicateOrder)
                .build();

        assertThatThrownBy(() -> tosspaymentsPaymentRepository.save(duplicatePayment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
