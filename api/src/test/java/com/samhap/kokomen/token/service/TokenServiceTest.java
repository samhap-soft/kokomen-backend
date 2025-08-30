package com.samhap.kokomen.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenServiceTest extends BaseTest {

    @Autowired
    private TokenService tokenService;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void FREE_토큰이_있을_때_FREE_토큰을_사용한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Token freeToken = tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(5)
                .build());
        Token paidToken = tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(10)
                .build());

        // when
        tokenService.useToken(member.getId());

        // then
        Token updatedFreeToken = tokenRepository.findById(freeToken.getId()).get();
        Token updatedPaidToken = tokenRepository.findById(paidToken.getId()).get();

        assertThat(updatedFreeToken.getTokenCount()).isEqualTo(4);
        assertThat(updatedPaidToken.getTokenCount()).isEqualTo(10); // 변화 없음
    }

    @Test
    void FREE_토큰이_없고_PAID_토큰이_있을_때_PAID_토큰을_사용한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Token freeToken = tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(0)
                .build());
        Token paidToken = tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(5)
                .build());

        // when
        tokenService.useToken(member.getId());

        // then
        Token updatedFreeToken = tokenRepository.findById(freeToken.getId()).get();
        Token updatedPaidToken = tokenRepository.findById(paidToken.getId()).get();

        assertThat(updatedFreeToken.getTokenCount()).isEqualTo(0); // 변화 없음
        assertThat(updatedPaidToken.getTokenCount()).isEqualTo(4);
    }

    @Test
    void FREE_토큰과_PAID_토큰이_모두_없을_때_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(0)
                .build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());

        // when & then
        assertThatThrownBy(() -> tokenService.useToken(member.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰을 이미 모두 소진하였습니다.");
    }
}
