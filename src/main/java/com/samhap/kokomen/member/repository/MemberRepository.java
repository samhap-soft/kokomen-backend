package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(Long kakaoId);

    @Modifying
    @Query("UPDATE Member m SET m.freeTokenCount = m.freeTokenCount - 1 WHERE m.id = :memberId AND m.freeTokenCount > 0")
    int decreaseFreeTokenCount(Long memberId);

    @Modifying
    @Query("UPDATE Member m SET m.freeTokenCount = :dailyFreeTokenCount")
    int rechargeDailyFreeToken(int dailyFreeTokenCount);

    @Query("""
                SELECT new com.samhap.kokomen.member.service.dto.RankingResponse(m, COUNT(i.id))
                FROM Member m
                LEFT JOIN Interview i ON i.member.id = m.id
                GROUP BY m
            """)
    List<RankingResponse> findRankings(Pageable pageable);
}
