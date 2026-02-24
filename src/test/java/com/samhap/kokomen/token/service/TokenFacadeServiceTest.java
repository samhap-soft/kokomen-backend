package com.samhap.kokomen.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.product.domain.TokenProduct;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenPurchaseState;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.dto.TokenPurchaseResponses;
import com.samhap.kokomen.token.repository.TokenPurchaseRepository;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class TokenFacadeServiceTest extends BaseTest {

    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenPurchaseRepository tokenPurchaseRepository;

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
        tokenFacadeService.useToken(member.getId());

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
        tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder().memberId(member.getId()).build());

        // when
        tokenFacadeService.useToken(member.getId());

        // then
        Token updatedFreeToken = tokenRepository.findById(freeToken.getId()).get();
        Token updatedPaidToken = tokenRepository.findById(paidToken.getId()).get();

        assertThat(updatedFreeToken.getTokenCount()).isZero(); // 변화 없음
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
        assertThatThrownBy(() -> tokenFacadeService.useToken(member.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰을 이미 모두 소진하였습니다.");
    }

    @Test
    void 데이터가_페이지_크기로_나누어떨어질_때_총_페이지_수가_정확히_계산된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // 10개의 토큰 구매 내역 생성
        for (int i = 0; i < 10; i++) {
            tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                    .memberId(member.getId())
                    .totalAmount(TokenProduct.TOKEN_10.getPrice())
                    .productName(TokenProduct.TOKEN_10.name())
                    .count(TokenProduct.TOKEN_10.getTokenCount())
                    .remainingCount(TokenProduct.TOKEN_10.getTokenCount())
                    .state(TokenPurchaseState.REFUNDABLE)
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 5); // 페이지 크기 5

        // when
        TokenPurchaseResponses result = tokenFacadeService.readMyTokenPurchases(member.getId(), null, pageable);

        // then
        assertThat(result.totalPageCount()).isEqualTo(2L); // 10개 데이터, 5개씩 → 2페이지
        assertThat(result.tokenPurchases()).hasSize(5); // 첫 번째 페이지는 5개
    }

    @Test
    void 데이터가_페이지_크기로_나누어떨어지지_않을_때_총_페이지_수가_정확히_계산된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // 7개의 토큰 구매 내역 생성
        for (int i = 0; i < 7; i++) {
            tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                    .memberId(member.getId())
                    .totalAmount(TokenProduct.TOKEN_10.getPrice())
                    .productName(TokenProduct.TOKEN_10.name())
                    .count(TokenProduct.TOKEN_10.getTokenCount())
                    .remainingCount(TokenProduct.TOKEN_10.getTokenCount())
                    .state(TokenPurchaseState.REFUNDABLE)
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 3); // 페이지 크기 3

        // when
        TokenPurchaseResponses result = tokenFacadeService.readMyTokenPurchases(member.getId(), null, pageable);

        // then
        assertThat(result.totalPageCount()).isEqualTo(3L); // 7개 데이터, 3개씩 → 3페이지 (3+3+1)
        assertThat(result.tokenPurchases()).hasSize(3); // 첫 번째 페이지는 3개
    }

    @Test
    void 데이터가_페이지_크기보다_적을_때_총_페이지_수가_1이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // 3개의 토큰 구매 내역 생성
        for (int i = 0; i < 3; i++) {
            tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                    .memberId(member.getId())
                    .totalAmount(TokenProduct.TOKEN_10.getPrice())
                    .productName(TokenProduct.TOKEN_10.name())
                    .count(TokenProduct.TOKEN_10.getTokenCount())
                    .remainingCount(TokenProduct.TOKEN_10.getTokenCount())
                    .state(TokenPurchaseState.REFUNDABLE)
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 10); // 페이지 크기 10

        // when
        TokenPurchaseResponses result = tokenFacadeService.readMyTokenPurchases(member.getId(), null, pageable);

        // then
        assertThat(result.totalPageCount()).isEqualTo(1L); // 3개 데이터, 10개씩 → 1페이지
        assertThat(result.tokenPurchases()).hasSize(3); // 모든 데이터가 첫 번째 페이지에
    }

    @Test
    void 데이터가_없을_때_총_페이지_수가_0이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        // 토큰 구매 내역을 생성하지 않음

        Pageable pageable = PageRequest.of(0, 10);

        // when
        TokenPurchaseResponses result = tokenFacadeService.readMyTokenPurchases(member.getId(), null, pageable);

        // then
        assertThat(result.totalPageCount()).isZero(); // 데이터 없음 → 0페이지
        assertThat(result.tokenPurchases()).isEmpty(); // 빈 리스트
    }

    @Test
    void 상태_필터링과_페이징이_함께_동작할_때_총_페이지_수가_정확히_계산된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // REFUNDABLE 상태 5개, USABLE 상태 3개 생성
        for (int i = 0; i < 5; i++) {
            tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                    .memberId(member.getId())
                    .totalAmount(TokenProduct.TOKEN_10.getPrice())
                    .productName(TokenProduct.TOKEN_10.name())
                    .count(TokenProduct.TOKEN_10.getTokenCount())
                    .remainingCount(TokenProduct.TOKEN_10.getTokenCount())
                    .state(TokenPurchaseState.REFUNDABLE)
                    .build());
        }

        for (int i = 0; i < 3; i++) {
            tokenPurchaseRepository.save(TokenPurchaseFixtureBuilder.builder()
                    .memberId(member.getId())
                    .totalAmount(TokenProduct.TOKEN_20.getPrice())
                    .productName(TokenProduct.TOKEN_20.name())
                    .count(TokenProduct.TOKEN_20.getTokenCount())
                    .remainingCount(TokenProduct.TOKEN_20.getTokenCount() - 5)
                    .state(TokenPurchaseState.USABLE)
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 3); // 페이지 크기 3

        // when - REFUNDABLE 상태만 필터링
        TokenPurchaseResponses result = tokenFacadeService.readMyTokenPurchases(member.getId(),
                TokenPurchaseState.REFUNDABLE, pageable);

        // then
        assertThat(result.totalPageCount()).isEqualTo(2L); // REFUNDABLE 5개, 3개씩 → 2페이지
        assertThat(result.tokenPurchases()).hasSize(3); // 첫 번째 페이지는 3개
        assertThat(result.tokenPurchases()).allMatch(purchase -> purchase.state().equals("환불 가능")); // 모두 REFUNDABLE 상태
    }
}
