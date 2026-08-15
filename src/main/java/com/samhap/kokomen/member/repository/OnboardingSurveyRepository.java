package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.survey.OnboardingSurvey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingSurveyRepository extends JpaRepository<OnboardingSurvey, Long> {

    Optional<OnboardingSurvey> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);
}
