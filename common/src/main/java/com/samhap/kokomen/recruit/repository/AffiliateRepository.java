package com.samhap.kokomen.recruit.repository;

import com.samhap.kokomen.recruit.domain.Affiliate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateRepository extends JpaRepository<Affiliate, String> {

    Optional<Affiliate> findByName(String name);
}
