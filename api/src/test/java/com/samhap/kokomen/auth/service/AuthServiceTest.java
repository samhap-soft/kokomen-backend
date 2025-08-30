package com.samhap.kokomen.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuthServiceTest extends BaseTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 이미_가입한_회원일지라도_프로필_완성을_못했다면_profileCompleted가_false이다() {
        // given
        Long kakaoId = 1L;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(kakaoId).build());

        // when
        Member readMember = memberService.readByKakaoId(kakaoId);

        // then
        assertThat(readMember.getProfileCompleted()).isFalse();
    }
}
