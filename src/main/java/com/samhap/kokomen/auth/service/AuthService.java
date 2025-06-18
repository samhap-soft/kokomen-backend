package com.samhap.kokomen.auth.service;

import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberService memberService;

    public MemberResponse kakaoLogin(String code) {
        KakaoUserInfoResponse kakaoUserInfoResponse = kakaoOAuthClient.requestKakaoUserInfo(code);

        return memberService.findOrCreateByKakaoId(kakaoUserInfoResponse.id(), kakaoUserInfoResponse.kakaoAccount().profile().nickname());
    }
}
