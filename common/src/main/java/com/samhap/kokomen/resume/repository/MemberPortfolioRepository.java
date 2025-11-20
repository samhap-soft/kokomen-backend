package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPortfolioRepository extends JpaRepository<MemberPortfolio, Long> {

    List<MemberPortfolio> findByMemberId(Long memberId);
}
