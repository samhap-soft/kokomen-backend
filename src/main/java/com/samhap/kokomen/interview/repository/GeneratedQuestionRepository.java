package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.entity.GeneratedQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Long> {

    List<GeneratedQuestion> findByGenerationIdOrderByQuestionOrder(Long generationId);
}
