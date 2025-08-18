package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RootQuestionRepositoryTest extends BaseTest {

    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void 사용자가_받지_않은_가장_첫번째_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findFirstQuestionMemberNotReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion3.getId());
    }

    @Test
    void 사용자가_마지막으로_받은_루트_질문_앞에_새로운_루트_질문들이_추가된_경우_그_중_첫번째_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findFirstQuestionMemberNotReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_인터뷰를_한번도_진행하지_않은_경우_맨_첫번째_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findFirstQuestionMemberNotReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion1.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_루트_질문을_모두_받은_경우_빈_값을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findFirstQuestionMemberNotReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 카테고리_중_사용자가_본_마지막_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findLastQuestionMemberReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }

    @Test
    void 사용자가_마지막으로_받은_루트_질문_앞에_새로운_루트_질문들이_추가되더라도_사용자가_본_마지막_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findLastQuestionMemberReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion3.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_루트_질문을_반복해서_받은_경우에도_사용자가_본_마지막_루트_질문을_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());

        // when
        Optional<RootQuestion> result =
                rootQuestionRepository.findLastQuestionMemberReceivedByCategory(Category.OPERATING_SYSTEM, member.getId(), RootQuestionState.ACTIVE);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }
}
