package com.samhap.kokomen.interview.repository;

import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeQuestionGenerationRepository extends JpaRepository<ResumeQuestionGeneration, Long> {

    boolean existsByMemberId(Long memberId);

    @EntityGraph(attributePaths = {"memberResume", "memberPortfolio"})
    Page<ResumeQuestionGeneration> findByMemberIdAndStateIn(
            Long memberId,
            List<ResumeQuestionGenerationState> states,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"memberResume", "memberPortfolio"})
    Page<ResumeQuestionGeneration> findByMemberIdAndState(
            Long memberId,
            ResumeQuestionGenerationState state,
            Pageable pageable
    );
}
