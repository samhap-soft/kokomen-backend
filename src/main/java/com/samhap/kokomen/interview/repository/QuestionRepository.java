package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByInterview(Interview interview);

    List<Question> findByInterviewOrderById(Interview interview);

    int countByInterview(Interview interview);

    @Query(value = "SELECT q.id FROM question q WHERE q.interview_id = :interviewId ORDER BY q.id ASC LIMIT 1", nativeQuery = true)
    Long findFirstQuestionIdByInterviewIdOrderByIdAsc(Long interviewId);

    List<Question> findTop2ByInterviewIdOrderByIdDesc(Long interviewId);
}
