package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationResult;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignEvaluationResultRepository extends JpaRepository<SystemDesignEvaluationResult, Long> {

    List<SystemDesignEvaluationResult> findBySystemDesignInterview(SystemDesignInterview systemDesignInterview);
}
