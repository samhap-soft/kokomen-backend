package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.resume.domain.MemberPortfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MemberPortfolioRepository extends JpaRepository<MemberPortfolio, Long> {

    List<MemberPortfolio> findByMemberId(Long memberId);

    Optional<MemberPortfolio> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 추출 텍스트 캐시를 content가 NULL일 때만 채운다. 이미 채워진 행은 0행 갱신으로 끝나 먼저 쓴 캐시를
     * 덮어쓰지 않고, content 외의 컬럼은 건드리지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MemberPortfolio p SET p.content = :content WHERE p.id = :id AND p.content IS NULL")
    int updateContentIfAbsent(@Param("id") Long id, @Param("content") String content);
}
