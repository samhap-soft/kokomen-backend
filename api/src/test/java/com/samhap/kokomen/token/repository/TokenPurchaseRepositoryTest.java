package com.samhap.kokomen.token.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenPurchaseRepositoryTest extends BaseTest {

    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 결제_내역에서_사용_가능한_유료_중_가장_오래된_내역을_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        TokenPurchase olderToken = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.USABLE)
                        .build());

        TokenPurchase newerToken = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.USABLE)
                        .build());

        // when
        Optional<TokenPurchase> firstToken = tokenPurchaseRepository.findFirstUsableTokenByState(member.getId(), TokenPurchaseState.USABLE);

        // then
        assertThat(firstToken.get().getId()).isEqualTo(olderToken.getId());
    }

    @Test
    void 결제_내역에서_사용_가능한_유료_토큰이_없으면_empty를_반환한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        TokenPurchase exhaustedToken = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.EXHAUSTED)
                        .remainingCount(0)
                        .build());

        // when
        Optional<TokenPurchase> firstToken = tokenPurchaseRepository.findFirstUsableTokenByState(member.getId(), TokenPurchaseState.USABLE);

        // then
        assertThat(firstToken).isEmpty();
    }
}
