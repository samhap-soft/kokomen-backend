package com.samhap.kokomen.payment.repository;

import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TosspaymentsPaymentResultRepository extends JpaRepository<TosspaymentsPaymentResult, Long> {

    Optional<TosspaymentsPaymentResult> findByTosspaymentsPaymentId(Long tosspaymentsPaymentId);
}
