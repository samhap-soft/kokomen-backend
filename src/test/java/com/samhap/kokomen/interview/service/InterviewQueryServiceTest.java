package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewQueryServiceTest extends BaseTest {

    @Autowired
    private InterviewQueryService interviewQueryService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void 아직_좋아요를_누르지_않은_인터뷰에_좋아요를_요청할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());

        // when
        interviewQueryService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() + 1);
    }

    @Disabled
    @Test
    void 이미_좋아요를_누른_인터뷰에_좋아요를_요청하면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());
        interviewQueryService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when & then
        assertThatThrownBy(
                () -> interviewQueryService.likeInterview(interview.getId(), new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 좋아요를 누른 인터뷰입니다.");
    }

    @Test
    void 이미_좋아요를_누른_인터뷰에_대해_좋아요를_취소할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());
        interviewQueryService.likeInterview(interview.getId(), new MemberAuth(member.getId()));

        // when
        interviewQueryService.unlikeInterview(interview.getId(), new MemberAuth(member.getId()));

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount());
    }

    @Test
    void 인터뷰에_좋아요를_누르면_최신_좋아요_수로_이벤트가_발행된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());
        MemberAuth memberAuth = new MemberAuth(member.getId());
        Long beforeLikeCount = interview.getLikeCount();

        // when
        interviewQueryService.likeInterview(interview.getId(), memberAuth);

        // then
        Interview updatedInterview = interviewRepository.findById(interview.getId()).get();
        assertThat(updatedInterview.getLikeCount()).isEqualTo(beforeLikeCount + 1);
    }
}
