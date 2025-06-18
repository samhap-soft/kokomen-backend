package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoId(Long kakaoId);
}
