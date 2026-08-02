package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 종단 상태 + 보존기간 경과 행의 원문만 만료 대상으로 뽑는다. 종단되지 않은 행은 질문 콜이 진행 중일 수
     * 있어 원문이 남아 있어야 하므로 상태 조건이 필수다.
     * JPQL LIMIT은 TosspaymentsPaymentRepository.findStalePaymentsByStates에 선례가 있다.
     */
    @Query("""
            SELECT s.analysis.id FROM ResumeAnalysisSourceText s
             WHERE s.analysis.state IN :terminalStates
               AND s.analysis.createdAt < :threshold
             ORDER BY s.analysis.id
             LIMIT :limit
            """)
    List<Long> findExpiredAnalysisIds(@Param("terminalStates") Collection<ResumeAnalysisState> terminalStates,
                                      @Param("threshold") LocalDateTime threshold,
                                      @Param("limit") int limit);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ResumeAnalysisSourceText s WHERE s.analysis.id IN :analysisIds")
    int deleteByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);
}
