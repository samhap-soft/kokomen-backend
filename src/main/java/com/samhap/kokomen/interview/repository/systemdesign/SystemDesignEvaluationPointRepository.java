package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignEvaluationPointRepository extends JpaRepository<SystemDesignEvaluationPoint, Long> {

    List<SystemDesignEvaluationPoint> findByProblemOrderByDisplayOrderAsc(SystemDesignProblem problem);
}
