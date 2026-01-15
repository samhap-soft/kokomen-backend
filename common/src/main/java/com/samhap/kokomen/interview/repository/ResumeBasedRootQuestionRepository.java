package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.ResumeBasedRootQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeBasedRootQuestionRepository extends JpaRepository<ResumeBasedRootQuestion, Long> {

    List<ResumeBasedRootQuestion> findByInterviewIdOrderByQuestionOrder(Long interviewId);

    int countByInterviewId(Long interviewId);
}
