package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPortfolioRepository extends JpaRepository<MemberPortfolio, Long> {

    List<MemberPortfolio> findByMemberId(Long memberId);

    Optional<MemberPortfolio> findByIdAndMemberId(Long id, Long memberId);
}
