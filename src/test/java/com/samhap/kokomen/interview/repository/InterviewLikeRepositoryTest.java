package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.InterviewLikeFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewLikeRepositoryTest extends BaseTest {

    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewLikeRepository interviewLikeRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 인터뷰들_중_특정_회원이_좋아요한_interviewIds를_조회한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Interview interview1 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Interview interview2 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());
        Interview interview3 = interviewRepository.save(InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());

        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().member(member).interview(interview1).build());
        interviewLikeRepository.save(InterviewLikeFixtureBuilder.builder().member(member).interview(interview3).build());

        List<Long> interviewIds = List.of(interview1.getId(), interview2.getId(), interview3.getId());

        // when
        List<Long> likedInterviewIds = interviewLikeRepository.findLikedInterviewIds(member.getId(), interviewIds);

        // then
        assertThat(likedInterviewIds).containsExactlyInAnyOrder(interview1.getId(), interview3.getId());
    }
}
