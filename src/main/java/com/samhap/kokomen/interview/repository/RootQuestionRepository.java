package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.RootQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RootQuestionRepository extends JpaRepository<RootQuestion, Long> {
}
