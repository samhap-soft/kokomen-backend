package com.samhap.kokomen.auth.service;

import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.MemberResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberRepository memberRepository;

    public MemberResponse kakaoLogin(String code) {
        KakaoUserInfoResponse kakaoUserInfoResponse = kakaoOAuthClient.requestKakaoUserInfo(code);
        Member member = memberRepository.findByKakaoId(kakaoUserInfoResponse.id())
                .orElseGet(() -> memberRepository.save(new Member(kakaoUserInfoResponse.id(), kakaoUserInfoResponse.kakaoAccount().profile().nickname())));

        return new MemberResponse(member);
    }
}
