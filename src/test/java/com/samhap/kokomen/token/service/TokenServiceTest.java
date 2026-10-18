package com.samhap.kokomen.token.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenPurchaseFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.MemberSchedulerService;
import com.samhap.kokomen.token.domain.TokenPurchase;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class TokenServiceTest extends BaseTest {

    private static final Duration CONCURRENT_UPDATE_WAIT = Duration.ofSeconds(2);

    @Autowired
    private TokenService tokenService;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private MemberSchedulerService memberSchedulerService;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 유료_토큰_차감과_지급이_겹쳐도_지급분이_유지된다() throws Exception {
        // given
        Member member = saveMemberWithTokens(0, 10);

        // when
        runBeforeConsumptionCommits(
                () -> tokenService.usePaidToken(member.getId()),
                () -> grantPaidTokens(member, 10));

        // then
        assertThat(readTokenCount(member, TokenType.PAID)).isEqualTo(10 - 1 + 10);
    }

    @Test
    void 유료_토큰_여러_개_차감과_지급이_겹쳐도_지급분이_유지된다() throws Exception {
        // given
        Member member = saveMemberWithTokens(0, 10);

        // when
        runBeforeConsumptionCommits(
                () -> tokenService.usePaidTokens(member.getId(), 3),
                () -> grantPaidTokens(member, 10));

        // then
        assertThat(readTokenCount(member, TokenType.PAID)).isEqualTo(10 - 3 + 10);
    }

    @Test
    void 무료_토큰_차감과_일일_재충전이_겹쳐도_재충전이_유지된다() throws Exception {
        // given
        Member member = saveMemberWithTokens(10, 0);

        // when
        runBeforeConsumptionCommits(
                () -> tokenService.useFreeToken(member.getId()),
                memberSchedulerService::rechargeDailyFreeToken);

        // then
        assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(TokenService.DAILY_FREE_TOKEN_COUNT);
    }

    @Test
    void 무료_토큰_여러_개_차감과_일일_재충전이_겹쳐도_재충전이_유지된다() throws Exception {
        // given
        Member member = saveMemberWithTokens(10, 0);

        // when
        runBeforeConsumptionCommits(
                () -> tokenService.useFreeTokens(member.getId(), 3),
                memberSchedulerService::rechargeDailyFreeToken);

        // then
        assertThat(readTokenCount(member, TokenType.FREE)).isEqualTo(TokenService.DAILY_FREE_TOKEN_COUNT);
    }

    /**
     * 차감을 트랜잭션 안에서 실행해 둔 채 다른 스레드가 잔액을 바꾸게 하고, 그 뒤에 차감을 커밋한다.
     * 지급과 일일 재충전은 token 분산 락을 잡지 않으므로 운영에서도 이 순서로 겹칠 수 있다.
     * 차감이 행 락을 쥐고 있으면 다른 스레드는 차감이 커밋될 때까지 막히므로, 제한 시간만 기다린 뒤 커밋하고
     * 그다음 다른 스레드가 끝나기를 기다린다.
     */
    private void runBeforeConsumptionCommits(Runnable consumption, Runnable concurrentUpdate) throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> concurrentUpdateResult = transactionTemplate.execute(status -> {
                consumption.run();
                Future<?> future = executorService.submit(concurrentUpdate);
                waitUntilDoneOrTimeout(future);
                return future;
            });
            concurrentUpdateResult.get(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdown();
        }
    }

    private void waitUntilDoneOrTimeout(Future<?> future) {
        try {
            future.get(CONCURRENT_UPDATE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 차감이 쥔 행 락에 막혀 있다. 차감이 커밋되면 이어서 실행된다.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private Member saveMemberWithTokens(int freeTokenCount, int paidTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(paidTokenCount).build());
        return member;
    }

    private void grantPaidTokens(Member member, int count) {
        TokenPurchase tokenPurchase = TokenPurchaseFixtureBuilder.builder()
                .memberId(member.getId())
                .purchaseCount(count)
                .build();
        tokenFacadeService.grantPurchasedTokens(tokenPurchase, count);
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }
}
