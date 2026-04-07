package com.samhap.kokomen.payment.repository;

import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.TosspaymentsPayment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TosspaymentsPaymentRepository extends JpaRepository<TosspaymentsPayment, Long> {

    Optional<TosspaymentsPayment> findByPaymentKey(String paymentKey);

    @Query("""
            SELECT p FROM TosspaymentsPayment p
            WHERE p.state IN :states
            AND p.updatedAt < :threshold
            ORDER BY p.updatedAt ASC
            LIMIT :limit
            """)
    List<TosspaymentsPayment> findStalePaymentsByStates(
            @Param("states") List<PaymentState> states,
            @Param("threshold") LocalDateTime threshold,
            @Param("limit") int limit
    );

    @Query("""
            SELECT p FROM TosspaymentsPayment p
            WHERE (:memberId IS NULL OR p.memberId = :memberId)
            AND (:state IS NULL OR p.state = :state)
            AND (:startDate IS NULL OR p.createdAt >= :startDate)
            AND (:endDate IS NULL OR p.createdAt <= :endDate)
            """)
    Page<TosspaymentsPayment> findPaymentsWithFilters(
            @Param("memberId") Long memberId,
            @Param("state") PaymentState state,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
