package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignRequirementQuestionRepository
        extends JpaRepository<SystemDesignRequirementQuestion, Long> {

    List<SystemDesignRequirementQuestion> findBySystemDesignInterviewOrderByIdAsc(
            SystemDesignInterview systemDesignInterview);

    long countBySystemDesignInterview(SystemDesignInterview systemDesignInterview);
}
