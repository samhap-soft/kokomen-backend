package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Answer a
            JOIN a.question q
            JOIN q.interview i
            WHERE a.id = :answerId AND i.member.id = :memberId
            """)
    boolean belongsToMember(@Param("answerId") Long answerId, @Param("memberId") Long memberId);

    List<Answer> findByQuestionIn(List<Question> questions);

    List<Answer> findByQuestionInOrderById(List<Question> questions);

    @Transactional
    @Modifying
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount + 1 WHERE a.id = :answerId")
    void incrementLikeCount(Long answerId);

    @Transactional
    @Modifying
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount - 1 WHERE a.id = :answerId")
    void decrementLikeCount(Long answerId);
}
