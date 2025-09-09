package com.samhap.kokomen.auth.service;

import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.service.dto.KakaoLoginRequest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.SocialProvider;
import com.samhap.kokomen.member.repository.MemberSocialLoginRepository;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.member.service.dto.MemberResponse;
import com.samhap.kokomen.token.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final MemberService memberService;
    private final MemberSocialLoginRepository memberSocialLoginRepository;
    private final TokenService tokenService;

    @Transactional
    public MemberResponse kakaoLogin(KakaoLoginRequest kakaoLoginRequest) {
        KakaoUserInfoResponse kakaoUserInfoResponse = kakaoOAuthClient.requestKakaoUserInfo(kakaoLoginRequest.code(), kakaoLoginRequest.redirectUri());

        return memberService.findBySocialLogin(SocialProvider.KAKAO, String.valueOf(kakaoUserInfoResponse.id()))
                .map(MemberResponse::new)
                .orElseGet(() -> {
                    Member member = memberService.saveSocialMember(SocialProvider.KAKAO, String.valueOf(kakaoUserInfoResponse.id()),
                            kakaoUserInfoResponse.kakaoAccount().profile().nickname());
                    tokenService.createTokensForNewMember(member.getId());
                    return new MemberResponse(member);
                });
    }

    @Transactional
    public void withdraw(MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());

        // 카카오 소셜로그인 정보 조회하여 카카오 연동해제
        memberSocialLoginRepository.findByMember_Id(member.getId()).stream()
                .filter(socialLogin -> socialLogin.getProvider() == SocialProvider.KAKAO)
                .findFirst()
                .ifPresent(kakaoLogin -> kakaoOAuthClient.unlinkKakaoUser(Long.valueOf(kakaoLogin.getSocialId())));

        memberService.withdraw(member);
    }

    @Transactional
    public void kakaoLogout(MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());

        // 카카오 소셜로그인 정보 조회하여 카카오 로그아웃
        memberSocialLoginRepository.findByMember_Id(member.getId()).stream()
                .filter(socialLogin -> socialLogin.getProvider() == SocialProvider.KAKAO)
                .findFirst()
                .ifPresent(kakaoLogin -> kakaoOAuthClient.logoutKakaoUser(Long.valueOf(kakaoLogin.getSocialId())));
    }
}
