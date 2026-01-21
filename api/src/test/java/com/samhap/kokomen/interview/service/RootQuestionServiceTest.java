package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RootQuestionServiceTest extends BaseTest {

    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private RootQuestionService rootQuestionService;

    /*
    아무것도 안본 경우, 중간에 비어 있는 경우, 딱 맟춰서 다 본경우, 초과해서 본경우, 일반적인 겨우
     */

    @Test
    void 사용자가_받지_않은_가장_첫번째_루트_질문을_반환한다() {
        // given
        InterviewRequest interviewRequest = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT);
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(
                        RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3)
                                .build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());

        // when
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);

        // then
        assertThat(rootQuestion)
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion3.getId());
    }

    @Test
    void 사용자가_마지막으로_받은_루트_질문_앞에_새로운_루트_질문들이_추가된_경우_그_중_첫번째_루트_질문을_반환한다() {
        // given
        InterviewRequest interviewRequest = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT);
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(
                        RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3)
                                .build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);

        // then
        assertThat(rootQuestion)
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_인터뷰를_한번도_진행하지_않은_경우_맨_첫번째_루트_질문을_반환한다() {
        // given
        InterviewRequest interviewRequest = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT);
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3).build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // when
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);

        // then
        assertThat(rootQuestion)
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion1.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_루트_질문을_정확하게_모두_받은_경우_첫번째_Order의_루트_질문을_반환한다() {
        // given
        InterviewRequest interviewRequest = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT);
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(
                        RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3)
                                .build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());

        // when
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);

        // then
        assertThat(rootQuestion)
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion1.getId());
    }

    @Test
    void 사용자가_해당_카테고리의_루트_질문을_모두_받고_초과해서_받는_경우_다음_루트_질문을_반환한다() {
        // given
        InterviewRequest interviewRequest = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT);
        RootQuestion operatingSystemRootQuestion1 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        RootQuestion operatingSystemRootQuestion2 = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(2).build());
        RootQuestion operatingSystemRootQuestion3 =
                rootQuestionRepository.save(
                        RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(3)
                                .build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion2).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion3).member(member).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().rootQuestion(operatingSystemRootQuestion1).member(member).build());

        // when
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);

        // then
        assertThat(rootQuestion)
                .extracting(RootQuestion::getId)
                .isEqualTo(operatingSystemRootQuestion2.getId());
    }
}
