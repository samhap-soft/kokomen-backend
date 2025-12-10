package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberResume;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberResumeRepository extends JpaRepository<MemberResume, Long> {

    List<MemberResume> findByMemberId(Long memberId);

    Optional<MemberResume> findByIdAndMemberId(Long id, Long memberId);
}
