package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.RootQuestion;
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
    void 사용자가_받지_않았던_루트_질문_중에서_랜덤으로_고른다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion otherRootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.DATA_STRUCTURE).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(otherRootQuestion).member(member).build());

        // when
        Optional<RootQuestion> result = rootQuestionRepository.findRandomByCategoryExcludingRecent(member.getId(), Category.OPERATING_SYSTEM.name(), 1);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }

    @Test
    void 사용자가_받았던_루트_질문이더라도_최근에_받지_않았다면_중복해서_고를_수_있다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion operatingSystemRootQuestion3 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        Optional<RootQuestion> result = rootQuestionRepository.findRandomByCategoryExcludingRecent(member.getId(), Category.OPERATING_SYSTEM.name(), 2);

        // then
        assertThat(result)
                .isPresent()
                .get()
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion1.getId());
    }

    @Test
    void 사용자가_최근에_받았던_질문을_제외했을때_남은_루트_질문이_없다면_empty를_반환한다() {
        // given
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestion operatingSystemRootQuestion3 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().category(Category.DATA_STRUCTURE).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        Optional<RootQuestion> result = rootQuestionRepository.findRandomByCategoryExcludingRecent(member.getId(), Category.OPERATING_SYSTEM.name(), 3);

        // then
        assertThat(result).isEmpty();
    }
}
