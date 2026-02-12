package com.samhap.kokomen.token.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenFacadeServiceConcurrencyTest extends BaseTest {

    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 동시에_토큰을_사용해도_정확히_차감된다() throws InterruptedException {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(10)
                .build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    tokenFacadeService.useToken(member.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Token updatedToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).get();
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isZero();
        assertThat(updatedToken.getTokenCount()).isZero();
    }

    @Test
    void 동시에_토큰_사용_시_잔여토큰보다_많은_요청은_실패한다() throws InterruptedException {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(3)
                .build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    tokenFacadeService.useToken(member.getId());
                    successCount.incrementAndGet();
                } catch (BadRequestException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Token updatedToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).get();
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failCount.get()).isEqualTo(7);
        assertThat(updatedToken.getTokenCount()).isZero();
    }

    @Test
    void 동시에_토큰을_일괄_사용해도_정확히_차감된다() throws InterruptedException {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.FREE)
                .tokenCount(15)
                .build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId())
                .type(TokenType.PAID)
                .tokenCount(0)
                .build());

        int threadCount = 5;
        int tokensPerRequest = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    tokenFacadeService.useTokens(member.getId(), tokensPerRequest);
                    successCount.incrementAndGet();
                } catch (BadRequestException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Token updatedToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).get();
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failCount.get()).isEqualTo(2);
        assertThat(updatedToken.getTokenCount()).isZero();
    }
}
