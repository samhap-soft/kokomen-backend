package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    boolean existsByIdAndQuestionInterviewMemberId(Long answerId, Long memberId);

    Optional<Answer> findByQuestionId(Long questionId);

    List<Answer> findByQuestionIn(List<Question> questions);

    List<Answer> findByQuestionInOrderById(List<Question> questions);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount + 1 WHERE a.id = :answerId")
    void incrementLikeCountModifying(Long answerId);

    @Transactional
    @Modifying
    @Query("UPDATE Answer a SET a.likeCount = a.likeCount - 1 WHERE a.id = :answerId")
    void decrementLikeCountModifying(Long answerId);

    @Query("""
        SELECT a
        FROM Answer a
        JOIN a.question q
        JOIN q.interview i
        WHERE i.rootQuestion.id = :rootQuestionId
        AND a.answerRank = :rank
        AND i.id != :excludeInterviewId
        ORDER BY i.likeCount DESC
        LIMIT :limit
        """)
    List<Answer> findTopAnswersByRootQuestionAndRank(Long rootQuestionId, AnswerRank rank, Long excludeInterviewId, int limit);
}
