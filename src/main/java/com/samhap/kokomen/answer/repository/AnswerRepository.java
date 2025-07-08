package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByQuestionIn(List<Question> questions);

    List<Answer> findByQuestionInOrderById(List<Question> questions);

    @Modifying
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount + 1 WHERE a.id = :answerId")
    void incrementLikeCount(Long answerId);

    @Modifying
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount - 1 WHERE a.id = :answerId")
    void decrementLikeCount(Long answerId);
}
