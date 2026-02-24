package com.samhap.kokomen.recruit.repository;

import com.samhap.kokomen.recruit.domain.Recruit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecruitRepository extends JpaRepository<Recruit, Long>, JpaSpecificationExecutor<Recruit> {

    boolean existsByExternalId(String externalId);
}
