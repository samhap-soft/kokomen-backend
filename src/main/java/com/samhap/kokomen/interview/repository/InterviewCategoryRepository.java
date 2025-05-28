package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.InterviewCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewCategoryRepository extends JpaRepository<InterviewCategory, Long> {
}
