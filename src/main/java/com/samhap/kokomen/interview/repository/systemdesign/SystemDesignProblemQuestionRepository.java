package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignProblemQuestionRepository extends JpaRepository<SystemDesignProblemQuestion, Long> {

    List<SystemDesignProblemQuestion> findByProblemOrderByQuestionOrderAsc(SystemDesignProblem problem);

    Optional<SystemDesignProblemQuestion> findByProblemAndQuestionOrder(SystemDesignProblem problem, int questionOrder);
}
