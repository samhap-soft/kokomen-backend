package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.interview.domain.AnswerLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerLikeRepository extends JpaRepository<AnswerLike, Long> {
    boolean existsByMemberIdAndAnswerId(Long memberId, Long answerId);

    int deleteByAnswerIdAndMemberId(Long answerId, Long memberId);
}
