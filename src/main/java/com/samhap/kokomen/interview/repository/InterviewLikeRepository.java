package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewLikeRepository extends JpaRepository<InterviewLike, Long> {

    int deleteByMemberAndInterview(Member member, Interview interview);
}
