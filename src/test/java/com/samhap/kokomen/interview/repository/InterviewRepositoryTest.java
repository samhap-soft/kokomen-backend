package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

class InterviewRepositoryTest extends BaseTest {

    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 좋아요_개수를_증가시킨다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());

        // when
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        interviewRepository.increaseLikeCount(interview.getId());
        transactionManager.commit(status);

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() + 1);
    }

    @Test
    void 좋아요_개수를_감소시킨다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());

        // when
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        interviewRepository.decreaseLikeCount(interview.getId());
        transactionManager.commit(status);

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() - 1);
    }
}
