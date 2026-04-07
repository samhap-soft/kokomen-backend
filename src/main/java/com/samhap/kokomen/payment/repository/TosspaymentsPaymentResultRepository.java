package com.samhap.kokomen.payment.repository;

import com.samhap.kokomen.payment.domain.TosspaymentsPaymentResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TosspaymentsPaymentResultRepository extends JpaRepository<TosspaymentsPaymentResult, Long> {

    Optional<TosspaymentsPaymentResult> findByTosspaymentsPaymentId(Long tosspaymentsPaymentId);

    @Query("SELECT r FROM TosspaymentsPaymentResult r WHERE r.tosspaymentsPayment.id IN :paymentIds")
    List<TosspaymentsPaymentResult> findByTosspaymentsPaymentIdIn(@Param("paymentIds") List<Long> paymentIds);
}
