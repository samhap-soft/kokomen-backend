package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.repository.dto.QuestionCountProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Long> {

    List<GeneratedQuestion> findByGenerationIdOrderByQuestionOrder(Long generationId);

    List<GeneratedQuestion> findByAnalysisIdOrderByQuestionOrder(Long analysisId);

    Optional<GeneratedQuestion> findByIdAndAnalysisId(Long id, Long analysisId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GeneratedQuestion q WHERE q.analysis.id IN :analysisIds")
    int deleteByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);

    @Query("SELECT q.analysis.id AS analysisId, COUNT(q) AS questionCount FROM GeneratedQuestion q "
            + "WHERE q.analysis.id IN :analysisIds GROUP BY q.analysis.id")
    List<QuestionCountProjection> countByAnalysisIdIn(@Param("analysisIds") List<Long> analysisIds);
}
