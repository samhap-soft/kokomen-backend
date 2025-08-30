package com.samhap.kokomen.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.dto.RankingProjection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberRepositoryTest extends BaseTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void findRankByScore() {
        // given
        Member ranker1 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(1L).score(100).build());
        Member ranker2 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(2L).score(50).build());
        Member ranker3 = memberRepository.save(MemberFixtureBuilder.builder().kakaoId(3L).score(0).build());

        // when
        long rank = memberRepository.findRankByScore(ranker2.getScore());

        // then
        assertThat(rank).isEqualTo(2L);
    }


    @Test
    void 완료한_총_인터뷰_수와_함께_랭킹을_조회한다() {
        // given
        Member member200 = memberRepository.save(MemberFixtureBuilder.builder().nickname("200점 회원").score(200).kakaoId(1L).build());
        Member member300 = memberRepository.save(MemberFixtureBuilder.builder().nickname("300점 회원").score(300).kakaoId(2L).build());
        memberRepository.save(MemberFixtureBuilder.builder().nickname("100점 회원").score(100).kakaoId(3L).build());
        memberRepository.save(MemberFixtureBuilder.builder().nickname("400점 회원").score(400).kakaoId(4L).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member200).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member300).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member300).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member300).rootQuestion(rootQuestion).interviewState(InterviewState.IN_PROGRESS).build());

        // when
        List<RankingProjection> rankingProjections = memberRepository.findRankings(2, 1);

        // then
        assertThat(rankingProjections).hasSize(2);
        assertThat(rankingProjections).extracting(RankingProjection::getNickname).containsExactly(member300.getNickname(), member200.getNickname());
        assertThat(rankingProjections).extracting(RankingProjection::getFinishedInterviewCount).containsExactly(2L, 1L);
    }
}
