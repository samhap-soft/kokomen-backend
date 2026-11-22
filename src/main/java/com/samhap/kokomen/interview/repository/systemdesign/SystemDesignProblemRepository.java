package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignProblemRepository extends JpaRepository<SystemDesignProblem, Long> {

    List<SystemDesignProblem> findByDifficultyAndState(SystemDesignDifficulty difficulty,
                                                       SystemDesignProblemState state);
}
