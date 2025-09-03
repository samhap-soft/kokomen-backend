package com.samhap.kokomen.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.service.dto.MemberStreakResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberServiceTest extends BaseTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;

    @Test
    void 스트릭_조회시_startDate와_endDate_필터링_경계_테스트() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // 2024-01-01, 2024-01-02, 2024-01-03에 인터뷰 생성
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 1));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 2));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 3));

        // when: 2024-01-01 ~ 2024-01-02 범위로 조회 (경계값 포함 테스트)
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 3)
        );

        assertThat(result.dailyCounts()).hasSize(3);
        assertThat(result.dailyCounts().get(0).date()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.dailyCounts().get(1).date()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(result.dailyCounts().get(2).date()).isEqualTo(LocalDate.of(2024, 1, 3));
    }

    @Test
    void 스트릭_조회시_startDate_이전_데이터는_필터링된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // startDate 이전과 이후에 인터뷰 생성
        createFinishedInterview(member, rootQuestion, LocalDate.of(2023, 12, 31));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 1));

        // when: 2024-01-01부터 조회
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        // then: 2024-01-01 데이터만 포함
        assertThat(result.dailyCounts()).hasSize(1);
        assertThat(result.dailyCounts().get(0).date()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void 스트릭_조회시_endDate_이후_데이터는_필터링된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // endDate 이전과 이후에 인터뷰 생성
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 31));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 2, 1));

        // when: 2024-01-31까지 조회
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        // then: 2024-01-31 데이터만 포함
        assertThat(result.dailyCounts()).hasSize(1);
        assertThat(result.dailyCounts().get(0).date()).isEqualTo(LocalDate.of(2024, 1, 31));
    }

    @Test
    void 스트릭_조회시_maxStreak_여러_연속일_중_최대값을_구한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // 첫 번째 스트릭: 3일 연속 (1/1, 1/2, 1/3)
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 1));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 2));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 3));

        // 간격 (1/4 없음)

        // 두 번째 스트릭: 5일 연속 (1/5 ~ 1/9)
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 5));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 6));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 7));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 8));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 9));

        // 간격 (1/10, 1/11 없음)

        // 세 번째 스트릭: 2일 연속 (1/12, 1/13)
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 12));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 13));

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        // then: 최대 스트릭은 5일
        assertThat(result.maxStreak()).isEqualTo(5);
    }

    @Test
    void 스트릭_조회시_maxStreak_연속일이_없으면_각각_1일씩_카운트된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // 불연속 날짜들 (1/1, 1/3, 1/5)
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 1));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 3));
        createFinishedInterview(member, rootQuestion, LocalDate.of(2024, 1, 5));

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        // then: 최대 스트릭은 1일
        assertThat(result.maxStreak()).isEqualTo(1);
    }

    @Test
    void 스트릭_조회시_maxStreak_인터뷰가_없으면_0이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        // then
        assertThat(result.maxStreak()).isEqualTo(0);
        assertThat(result.currentStreak()).isEqualTo(0);
    }

    @Test
    void 스트릭_조회시_currentStreak_오늘_풀었을_때_연속일_계산() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        // 3일 연속으로 인터뷰 완료 (오늘까지)
        createFinishedInterview(member, rootQuestion, twoDaysAgo);
        createFinishedInterview(member, rootQuestion, yesterday);
        createFinishedInterview(member, rootQuestion, today);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 현재 스트릭은 3일
        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    void 스트릭_조회시_currentStreak_오늘_안풀고_어제_풀었을_때_연속일_계산() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);
        LocalDate threeDaysAgo = today.minusDays(3);

        // 어제까지 3일 연속 인터뷰 완료 (오늘은 안함)
        createFinishedInterview(member, rootQuestion, threeDaysAgo);
        createFinishedInterview(member, rootQuestion, twoDaysAgo);
        createFinishedInterview(member, rootQuestion, yesterday);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 어제까지의 연속일이므로 현재 스트릭은 3일
        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    void 스트릭_조회시_currentStreak_어제_오늘_모두_안풀었을_때_0이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);

        // 3일 전에 인터뷰 완료 (어제, 오늘은 안함)
        createFinishedInterview(member, rootQuestion, threeDaysAgo);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 어제, 오늘 모두 안 풀었으므로 현재 스트릭은 0
        assertThat(result.currentStreak()).isEqualTo(0);
    }

    @Test
    void 스트릭_조회시_currentStreak_중간에_빈_날이_있으면_연속_끊어진다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate threeDaysAgo = today.minusDays(3); // 2일 전은 건너뛰기

        // 어제와 오늘 인터뷰 완료, 하지만 2일 전은 건너뜀
        createFinishedInterview(member, rootQuestion, threeDaysAgo);
        createFinishedInterview(member, rootQuestion, yesterday);
        createFinishedInterview(member, rootQuestion, today);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 어제~오늘 연속이므로 현재 스트릭은 2일
        assertThat(result.currentStreak()).isEqualTo(2);
    }

    @Test
    void 스트릭_조회시_currentStreak_혼자_1일만_풀었을_때() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();

        // 오늘만 인터뷰 완료
        createFinishedInterview(member, rootQuestion, today);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 현재 스트릭은 1일
        assertThat(result.currentStreak()).isEqualTo(1);
    }

    @Test
    void 스트릭_조회시_currentStreak_어제만_1일_풀었을_때() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 어제만 인터뷰 완료
        createFinishedInterview(member, rootQuestion, yesterday);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 현재 스트릭은 1일
        assertThat(result.currentStreak()).isEqualTo(1);
    }

    @Test
    void 스트릭_조회시_같은_날_여러_인터뷰를_완료해도_1일로_카운트된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        MemberAuth memberAuth = new MemberAuth(member.getId());

        LocalDate today = LocalDate.now();

        // 같은 날에 여러 인터뷰 완료
        createFinishedInterview(member, rootQuestion, today);
        createFinishedInterview(member, rootQuestion, today);
        createFinishedInterview(member, rootQuestion, today);

        // when
        MemberStreakResponse result = memberService.findMemberStreaks(
                memberAuth,
                today.minusMonths(1),
                today.plusMonths(1)
        );

        // then: 같은 날 여러 인터뷰도 1일로 카운트
        assertThat(result.dailyCounts()).hasSize(1);
        assertThat(result.dailyCounts().get(0).count()).isEqualTo(3L);
        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak()).isEqualTo(1);
    }

    private void createFinishedInterview(Member member, RootQuestion rootQuestion, LocalDate finishedDate) {
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED)
                .totalFeedback("테스트 피드백")
                .totalScore(85)
                .finishedAt(finishedDate.atTime(12, 0))
                .build());
    }
}
