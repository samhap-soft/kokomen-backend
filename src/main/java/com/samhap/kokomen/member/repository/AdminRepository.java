package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    boolean existsByMemberId(Long memberId);
}
