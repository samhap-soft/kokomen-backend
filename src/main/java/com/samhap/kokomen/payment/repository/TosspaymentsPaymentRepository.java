package com.samhap.kokomen.payment.repository;

import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TosspaymentsPaymentRepository extends JpaRepository<TosspaymentsPayment, Long> {

    Optional<TosspaymentsPayment> findByPaymentKey(String paymentKey);
}
