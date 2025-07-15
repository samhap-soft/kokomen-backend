package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.AnswerLike;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerLikeRepository extends JpaRepository<AnswerLike, Long> {
    boolean existsByMemberIdAndAnswerId(Long memberId, Long answerId);

    int deleteByAnswerIdAndMemberId(Long answerId, Long memberId);

    @Query("SELECT al.answer.id FROM AnswerLike al WHERE al.member.id = :memberId AND al.answer.id IN :answerIds")
    Set<Long> findLikedAnswerIds(@Param("memberId") Long memberId, @Param("answerIds") List<Long> answerIds);
}
