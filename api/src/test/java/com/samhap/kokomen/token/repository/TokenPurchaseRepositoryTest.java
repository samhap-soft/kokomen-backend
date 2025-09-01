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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

    @Test
    void 회원별_토큰_구매_내역을_페이징하여_조회한다() {
        // given
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1001L).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1002L).build());

        // member1의 토큰 구매 내역 3개 생성
        TokenPurchase purchase1 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member1.getId())
                        .totalAmount(100L)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build());
        
        TokenPurchase purchase2 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member1.getId())
                        .totalAmount(200L)
                        .state(TokenPurchaseState.USABLE)
                        .build());
        
        TokenPurchase purchase3 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member1.getId())
                        .totalAmount(300L)
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build());

        // member2의 토큰 구매 내역 1개 생성 (다른 회원 데이터 확인용)
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member2.getId())
                        .totalAmount(999L)
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build());

        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id"));

        // when
        Page<TokenPurchase> result = tokenPurchaseRepository.findByMemberId(member1.getId(), pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.hasNext()).isTrue();
        
        // id 내림차순으로 정렬되어 있는지 확인
        assertThat(result.getContent().get(0).getId()).isEqualTo(purchase3.getId());
        assertThat(result.getContent().get(1).getId()).isEqualTo(purchase2.getId());
    }

    @Test
    void 회원별_토큰_구매_내역_페이징_조회_두번째_페이지() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // 토큰 구매 내역 3개 생성
        TokenPurchase purchase1 = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .build());
        
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .build());
        
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .build());

        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id"));

        // when
        Page<TokenPurchase> result = tokenPurchaseRepository.findByMemberId(member.getId(), pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isTrue();
        assertThat(result.hasNext()).isFalse();
        
        // 가장 오래된 항목이 마지막 페이지에 있는지 확인
        assertThat(result.getContent().get(0).getId()).isEqualTo(purchase1.getId());
    }

    @Test
    void 토큰_구매_내역이_없는_회원의_페이징_조회() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        // when
        Page<TokenPurchase> result = tokenPurchaseRepository.findByMemberId(member.getId(), pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isTrue();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 회원별_특정_상태의_토큰_구매_내역을_페이징하여_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // 다양한 상태의 토큰 구매 내역 생성
        TokenPurchase refundable = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build());

        TokenPurchase usable = tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.USABLE)
                        .build());

        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.EXHAUSTED)
                        .build());

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        // when - REFUNDABLE 상태만 조회
        Page<TokenPurchase> refundableResult = tokenPurchaseRepository.findByMemberIdAndState(member.getId(), TokenPurchaseState.REFUNDABLE, pageable);

        // then
        assertThat(refundableResult.getContent()).hasSize(1);
        assertThat(refundableResult.getTotalElements()).isEqualTo(1);
        assertThat(refundableResult.getContent().get(0).getId()).isEqualTo(refundable.getId());
        assertThat(refundableResult.getContent().get(0).getState()).isEqualTo(TokenPurchaseState.REFUNDABLE);
    }

    @Test
    void 회원별_존재하지_않는_상태로_토큰_구매_내역_조회시_빈_결과를_반환한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // REFUNDABLE 상태만 생성
        tokenPurchaseRepository.save(
                TokenPurchaseFixtureBuilder.builder()
                        .memberId(member.getId())
                        .state(TokenPurchaseState.REFUNDABLE)
                        .build());

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        // when - EXHAUSTED 상태로 조회 (존재하지 않음)
        Page<TokenPurchase> result = tokenPurchaseRepository.findByMemberIdAndState(member.getId(), TokenPurchaseState.EXHAUSTED, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
