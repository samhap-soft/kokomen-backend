package com.samhap.kokomen.recruit.repository;

import com.samhap.kokomen.recruit.domain.Affiliate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateRepository extends JpaRepository<Affiliate, String> {
}
