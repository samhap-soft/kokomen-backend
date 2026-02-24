package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeEvaluationRepository extends JpaRepository<ResumeEvaluation, Long> {

    Page<ResumeEvaluation> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
}
