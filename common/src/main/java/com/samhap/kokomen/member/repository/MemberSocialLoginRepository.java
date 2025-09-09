package com.samhap.kokomen.member.repository;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.MemberSocialLogin;
import com.samhap.kokomen.member.domain.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberSocialLoginRepository extends JpaRepository<MemberSocialLogin, Long> {

    Optional<MemberSocialLogin> findByProviderAndSocialId(SocialProvider provider, String socialId);

    List<MemberSocialLogin> findByMember(Member member);
    
    List<MemberSocialLogin> findByMember_Id(Long memberId);
}