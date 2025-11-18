package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPortfolioRepository extends JpaRepository<MemberPortfolio, Long> {
}
