package com.samhap.kokomen.auth.controller;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.MemberSocialLogin;
import com.samhap.kokomen.member.domain.SocialProvider;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.MemberSocialLoginRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local|load-test|dev")
@RequiredArgsConstructor
@RestController
public class AuthTestController {

    private final MemberRepository memberRepository;
    private final MemberSocialLoginRepository memberSocialLoginRepository;

    @PostMapping("/test/register/{kakaoId}")
    public ResponseEntity<Long> setTestSession(
            @PathVariable Long kakaoId,
            HttpServletRequest request) {
        Member member = memberSocialLoginRepository.findByProviderAndSocialId(SocialProvider.KAKAO, String.valueOf(kakaoId))
                .map(MemberSocialLogin::getMember)
                .orElseGet(() -> {
                    Member newMember = memberRepository.save(new Member("테스트 회원_" + kakaoId));
                    memberSocialLoginRepository.save(new MemberSocialLogin(newMember, SocialProvider.KAKAO, String.valueOf(kakaoId)));
                    return newMember;
                });
        HttpSession session = request.getSession(true);
        session.setAttribute("MEMBER_ID", member.getId());

        return ResponseEntity.ok(member.getId());
    }
}
