package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
}
