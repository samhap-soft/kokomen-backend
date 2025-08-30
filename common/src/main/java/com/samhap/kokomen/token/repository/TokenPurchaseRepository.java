package com.samhap.kokomen.token.repository;

import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenPurchaseRepository extends JpaRepository<TokenPurchase, Long> {

    @Query("""
            SELECT tp FROM TokenPurchase tp 
            WHERE tp.memberId = :memberId 
            AND tp.state = :state 
            AND tp.remainingCount > 0 
            ORDER BY tp.id ASC 
            LIMIT 1
            """)
    Optional<TokenPurchase> findFirstUsableTokenByState(@Param("memberId") Long memberId, @Param("state") TokenPurchaseState state);
}
