package com.samhap.kokomen.auth.controller;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local|load-test")
@RequiredArgsConstructor
@RestController
public class AuthLocalController {

    private final MemberRepository memberRepository;

    @PostMapping("/local/register/{kakaoId}")
    public ResponseEntity<Long> setTestSession(
            @PathVariable Long kakaoId,
            HttpServletRequest request) {
        Member member = memberRepository.findById(kakaoId)
                .orElseGet(() -> memberRepository.save(new Member(kakaoId, "테스트 회원_" + kakaoId)));
        HttpSession session = request.getSession(true);
        session.setAttribute("MEMBER_ID", member.getId());

        return ResponseEntity.ok(member.getId());
    }
}
