package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ResumeAnalysisSourceTextRepository extends JpaRepository<ResumeAnalysisSourceText, Long> {

    Optional<ResumeAnalysisSourceText> findByAnalysisId(Long analysisId);

    boolean existsByAnalysisId(Long analysisId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ResumeAnalysisSourceText s WHERE s.analysis.id IN :analysisIds")
    int deleteByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);
}
