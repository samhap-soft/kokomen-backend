package com.samhap.kokomen.recruit.repository;

import com.samhap.kokomen.recruit.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, String> {
}
