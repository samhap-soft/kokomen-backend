package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(Long kakaoId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.freeTokenCount = m.freeTokenCount - 1 WHERE m.id = :memberId AND m.freeTokenCount > 0")
    int decreaseFreeTokenCount(Long memberId);

    @Modifying
    @Query("UPDATE Member m SET m.freeTokenCount = :dailyFreeTokenCount")
    int rechargeDailyFreeToken(int dailyFreeTokenCount);
}
