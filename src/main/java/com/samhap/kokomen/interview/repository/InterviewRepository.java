package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByMember(Member member, Pageable pageable);

    List<Interview> findByMemberAndInterviewState(Member member, InterviewState interviewState, Pageable pageable);

    Long countByMemberAndInterviewState(Member member, InterviewState interviewState);

    @Transactional
    @Modifying
    @Query("UPDATE Interview i SET i.likeCount = i.likeCount + 1 WHERE i.id = :interviewId")
    void increaseLikeCountModifying(Long interviewId);

    @Transactional
    @Modifying
    @Query("UPDATE Interview i SET i.likeCount = i.likeCount - 1 WHERE i.id = :interviewId")
    void decreaseLikeCountModifying(Long interviewId);
}
