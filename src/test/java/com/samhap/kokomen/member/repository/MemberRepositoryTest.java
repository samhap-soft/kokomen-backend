package com.samhap.kokomen.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

class MemberRepositoryTest extends BaseTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

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

    @Test
    void 총_인터뷰_수와_함께_랭킹을_조회한다() {
        // given
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().nickname("member1").score(200).kakaoId(1L).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("member2").score(300).kakaoId(2L).build());
        Member member3 = memberRepository.save(MemberFixtureBuilder.builder().nickname("member3").score(100).kakaoId(3L).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        interviewRepository.save(InterviewFixtureBuilder.builder().member(member1).rootQuestion(rootQuestion).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion).build());

        Pageable pageable = PageRequest.of(0, 2, Sort.by("score").descending());

        // when
        List<RankingResponse> rankings = memberRepository.findRankings(pageable);

        // then
        assertThat(rankings).hasSize(2);
        assertThat(rankings).extracting("nickname").containsExactly("member2", "member1");
        assertThat(rankings).extracting("interviewCount").containsExactly(2, 1);
    }
}
