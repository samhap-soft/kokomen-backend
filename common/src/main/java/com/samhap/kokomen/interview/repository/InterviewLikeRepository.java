package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewLikeRepository extends JpaRepository<InterviewLike, Long> {

    int deleteByMemberAndInterview(Member member, Interview interview);

    boolean existsByInterviewIdAndMemberId(Long interviewId, Long memberId);

    @Query("SELECT il.interview.id FROM InterviewLike il WHERE il.member.id = :memberId AND il.interview.id IN :interviewIds")
    Set<Long> findLikedInterviewIds(@Param("memberId") Long memberId, @Param("interviewIds") List<Long> interviewIds);
}
