package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemDesignAnswerRepository extends JpaRepository<SystemDesignAnswer, Long> {

    List<SystemDesignAnswer> findByQuestionIn(Collection<SystemDesignQuestion> questions);

    Optional<SystemDesignAnswer> findByQuestion(SystemDesignQuestion question);

    boolean existsByQuestion(SystemDesignQuestion question);
}
