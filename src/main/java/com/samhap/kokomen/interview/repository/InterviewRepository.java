package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByMember(Member member, Pageable pageable);

    List<Interview> findByMemberAndInterviewState(Member member, InterviewState interviewState, Pageable pageable);
}
