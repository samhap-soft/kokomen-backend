package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Long> {

    List<GeneratedQuestion> findByGenerationIdOrderByQuestionOrder(Long generationId);
}
