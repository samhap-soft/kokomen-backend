package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByInterview(Interview interview);

    List<Question> findByInterviewOrderById(Interview interview);

    int countByInterview(Interview interview);
}
