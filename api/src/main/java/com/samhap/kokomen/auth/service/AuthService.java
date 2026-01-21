package com.samhap.kokomen.auth.service;

import com.samhap.kokomen.auth.external.GoogleOAuthClient;
import com.samhap.kokomen.auth.external.KakaoOAuthClient;
import com.samhap.kokomen.auth.external.dto.GoogleUserInfoResponse;
import com.samhap.kokomen.auth.external.dto.KakaoUserInfoResponse;
import com.samhap.kokomen.auth.service.dto.GoogleLoginRequest;
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
    private final GoogleOAuthClient googleOAuthClient;
    private final MemberService memberService;
    private final MemberSocialLoginRepository memberSocialLoginRepository;
    private final TokenService tokenService;

    @Transactional
    public MemberResponse kakaoLogin(KakaoLoginRequest kakaoLoginRequest) {
        KakaoUserInfoResponse kakaoUserInfoResponse = kakaoOAuthClient.requestKakaoUserInfo(kakaoLoginRequest.code(),
                kakaoLoginRequest.redirectUri());

        return memberService.findBySocialLogin(SocialProvider.KAKAO, String.valueOf(kakaoUserInfoResponse.id()))
                .map(MemberResponse::new)
                .orElseGet(() -> {
                    Member member = memberService.saveSocialMember(SocialProvider.KAKAO,
                            String.valueOf(kakaoUserInfoResponse.id()),
                            kakaoUserInfoResponse.kakaoAccount().profile().nickname());
                    tokenService.createTokensForNewMember(member.getId());
                    return new MemberResponse(member);
                });
    }

    @Transactional
    public MemberResponse googleLogin(GoogleLoginRequest googleLoginRequest) {
        GoogleUserInfoResponse googleUserInfoResponse = googleOAuthClient.requestGoogleUserInfo(
                googleLoginRequest.code(), googleLoginRequest.redirectUri());

        return memberService.findBySocialLogin(SocialProvider.GOOGLE, googleUserInfoResponse.id())
                .map(MemberResponse::new)
                .orElseGet(() -> {
                    Member member = memberService.saveSocialMember(SocialProvider.GOOGLE, googleUserInfoResponse.id(),
                            googleUserInfoResponse.name());
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

        // 구글 소셜로그인 정보 조회하여 구글 연동해제
        memberSocialLoginRepository.findByMember_Id(member.getId()).stream()
                .filter(socialLogin -> socialLogin.getProvider() == SocialProvider.GOOGLE)
                .findFirst()
                .ifPresent(googleLogin -> googleOAuthClient.revokeGoogleToken(googleLogin.getSocialId()));

        memberService.withdraw(member);
    }

    @Transactional
    public void logout(MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());

        // 모든 소셜로그인 제공자에 대해 로그아웃 처리
        memberSocialLoginRepository.findByMember_Id(member.getId()).forEach(socialLogin -> {
            if (socialLogin.getProvider() == SocialProvider.KAKAO) {
                kakaoOAuthClient.logoutKakaoUser(Long.valueOf(socialLogin.getSocialId()));
            } else if (socialLogin.getProvider() == SocialProvider.GOOGLE) {
                googleOAuthClient.revokeGoogleToken(socialLogin.getSocialId());
            }
        });
    }
}
