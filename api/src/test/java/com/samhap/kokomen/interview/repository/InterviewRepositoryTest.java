package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.dto.DailyInterviewCount;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewRepositoryTest extends BaseTest {

    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 좋아요_개수를_증가시킨다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(0L).build());

        // when
        interviewRepository.increaseLikeCountModifying(interview.getId());

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() + 1);
    }

    @Test
    void 좋아요_개수를_감소시킨다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).likeCount(1L).build());

        // when
        interviewRepository.decreaseLikeCountModifying(interview.getId());

        // then
        Interview found = interviewRepository.findById(interview.getId()).get();
        assertThat(found.getLikeCount()).isEqualTo(interview.getLikeCount() - 1);
    }

    @Test
    void interviewId로_rootQuestionId를_조회할_수_있다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Interview interview = interviewRepository.save(
                InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).build());

        // when
        Long foundRootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interview.getId());

        // then
        assertThat(foundRootQuestionId).isEqualTo(rootQuestion.getId());
    }

    @Test
    void 멤버ID로_완료된_인터뷰의_날짜별_카운트를_조회할_수_있다() {
        // given
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Member member1 = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().build());

        LocalDate today = LocalDate.of(2024, 1, 15);
        LocalDate yesterday = today.minusDays(1);

        // member1의 완료된 인터뷰들 - 같은 날짜에 여러 개, 다른 날짜에도 있음
        createFinishedInterview(member1, rootQuestion, today.atTime(10, 0));
        createFinishedInterview(member1, rootQuestion, today.atTime(14, 0));
        createFinishedInterview(member1, rootQuestion, yesterday.atTime(9, 0));

        // member1의 진행중인 인터뷰 (결과에 포함되면 안됨)
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member1)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.IN_PROGRESS)
                .build());

        // member2의 완료된 인터뷰 (결과에 포함되면 안됨)
        createFinishedInterview(member2, rootQuestion, today.atTime(11, 0));

        // when
        List<DailyInterviewCount> result = interviewRepository.countFinishedInterviewsByMemberId(member1.getId());

        // then
        assertThat(result).hasSize(2);

        // 날짜순으로 정렬되어 반환되는지 확인
        assertThat(result.get(0).date()).isEqualTo(yesterday);
        assertThat(result.get(0).count()).isEqualTo(1L);

        assertThat(result.get(1).date()).isEqualTo(today);
        assertThat(result.get(1).count()).isEqualTo(2L);
    }

    @Test
    void 완료된_인터뷰가_없는_멤버는_빈_리스트를_반환한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // when
        List<DailyInterviewCount> result = interviewRepository.countFinishedInterviewsByMemberId(member.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 존재하지_않는_멤버ID로_조회하면_빈_리스트를_반환한다() {
        // given
        Long nonExistentMemberId = 999999L;

        // when
        List<DailyInterviewCount> result = interviewRepository.countFinishedInterviewsByMemberId(nonExistentMemberId);

        // then
        assertThat(result).isEmpty();
    }

    private void createFinishedInterview(Member member, RootQuestion rootQuestion, LocalDateTime finishedAt) {
        Interview interview = InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED)
                .totalFeedback("테스트 피드백")
                .totalScore(85)
                .finishedAt(finishedAt)
                .build();

        interviewRepository.save(interview);
    }
}
