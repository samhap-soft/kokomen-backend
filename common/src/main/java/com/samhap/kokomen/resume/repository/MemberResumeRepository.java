package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberResume;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberResumeRepository extends JpaRepository<MemberResume, Long> {

    Optional<MemberResume> findByMemberId(Long memberId);
}
