package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignInterviewRepository extends JpaRepository<SystemDesignInterview, Long> {

    @EntityGraph(attributePaths = {"interview", "problem"})
    Optional<SystemDesignInterview> findByInterviewId(Long interviewId);
}
