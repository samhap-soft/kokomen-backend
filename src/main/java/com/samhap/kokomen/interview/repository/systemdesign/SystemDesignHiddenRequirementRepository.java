package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignHiddenRequirementRepository extends JpaRepository<SystemDesignHiddenRequirement, Long> {

    List<SystemDesignHiddenRequirement> findByProblemOrderByDisplayOrderAsc(SystemDesignProblem problem);
}
