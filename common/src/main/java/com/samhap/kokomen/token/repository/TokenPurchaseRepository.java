package com.samhap.kokomen.token.repository;

import com.samhap.kokomen.token.domain.TokenPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenPurchaseRepository extends JpaRepository<TokenPurchase, Long> {
}