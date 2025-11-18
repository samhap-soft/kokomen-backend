package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberResume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberResumeRepository extends JpaRepository<MemberResume, Long> {
}
