package com.samhap.kokomen.auth.service;

import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.service.dto.KakaoLoginRequest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberService memberService;

    public MemberResponse kakaoLogin(KakaoLoginRequest kakaoLoginRequest) {
        KakaoUserInfoResponse kakaoUserInfoResponse = kakaoOAuthClient.requestKakaoUserInfo(kakaoLoginRequest.code(), kakaoLoginRequest.redirectUri());

        return memberService.findOrCreateByKakaoId(kakaoUserInfoResponse.id(), kakaoUserInfoResponse.kakaoAccount().profile().nickname());
    }

    @Transactional
    public void withdraw(MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        kakaoOAuthClient.unlinkKakaoUser(member.getKakaoId());
        memberService.withdraw(member);
    }

    @Transactional
    public void kakaoLogout(MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        kakaoOAuthClient.logoutKakaoUser(member.getKakaoId());
    }
}
