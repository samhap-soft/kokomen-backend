package com.samhap.kokomen.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.MemberSocialLogin;
import com.samhap.kokomen.member.domain.SocialProvider;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.MemberSocialLoginRepository;
import com.samhap.kokomen.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthServiceTest extends BaseTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberSocialLoginRepository memberSocialLoginRepository;

    @Test
    void 이미_가입한_회원일지라도_프로필_완성을_못했다면_profileCompleted가_false이다() {
        // given
        Long kakaoId = 1L;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        memberSocialLoginRepository.save(new MemberSocialLogin(member, SocialProvider.KAKAO, String.valueOf(kakaoId)));

        // when
        Member readMember = memberService.readBySocialLogin(SocialProvider.KAKAO, String.valueOf(kakaoId));

        // then
        assertThat(readMember.getProfileCompleted()).isFalse();
    }
}
