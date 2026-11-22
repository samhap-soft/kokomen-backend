package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignQuestionRepository extends JpaRepository<SystemDesignQuestion, Long> {

    List<SystemDesignQuestion> findBySystemDesignInterviewOrderByQuestionOrderAsc(
            SystemDesignInterview systemDesignInterview);

    Optional<SystemDesignQuestion> findBySystemDesignInterviewAndQuestionOrder(
            SystemDesignInterview systemDesignInterview, int questionOrder);
}
