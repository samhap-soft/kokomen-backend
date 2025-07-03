package com.samhap.kokomen.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

class MemberRepositoryTest extends BaseTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void free_token_count를_1_감소시킨다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().freeTokenCount(1).build());

        // when
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        int affectedRows = memberRepository.decreaseFreeTokenCount(member.getId());
        transactionManager.commit(status);

        // then
        assertAll(
                () -> assertThat(memberRepository.findById(member.getId()).get().getFreeTokenCount()).isZero(),
                () -> assertThat(affectedRows).isEqualTo(1)
        );
    }

    @Test
    void free_token_count가_부족하면_0을_반환한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().freeTokenCount(0).build());

        // when
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        int affectedRows = memberRepository.decreaseFreeTokenCount(member.getId());
        transactionManager.commit(status);

        // then
        assertAll(
                () -> assertThat(memberRepository.findById(member.getId()).get().getFreeTokenCount()).isZero(),
                () -> assertThat(affectedRows).isEqualTo(0)
        );
    }

    @Test
    void daily_free_token_count를_재충전한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().freeTokenCount(0).build());

        // when
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        int affectedRows = memberRepository.rechargeDailyFreeToken(Member.DAILY_FREE_TOKEN_COUNT);
        transactionManager.commit(status);

        // then
        assertAll(
                () -> assertThat(memberRepository.findById(member.getId()).get().getFreeTokenCount()).isEqualTo(Member.DAILY_FREE_TOKEN_COUNT),
                () -> assertThat(affectedRows).isEqualTo(1)
        );
    }
}
