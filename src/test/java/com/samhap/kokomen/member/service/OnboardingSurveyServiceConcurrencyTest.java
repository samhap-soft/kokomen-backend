package com.samhap.kokomen.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.survey.CareerGoal;
import com.samhap.kokomen.member.domain.survey.InterviewExperience;
import com.samhap.kokomen.member.domain.survey.OnboardingSurvey;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.TargetCompanyType;
import com.samhap.kokomen.member.domain.survey.WeakPoint;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.OnboardingSurveyRepository;
import com.samhap.kokomen.member.service.dto.OnboardingSurveySubmitRequest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OnboardingSurveyServiceConcurrencyTest extends BaseTest {

    private static final long AWAIT_TIMEOUT_SECONDS = 10L;

    @Autowired
    private OnboardingSurveyService onboardingSurveyService;
    @Autowired
    private OnboardingSurveyRepository onboardingSurveyRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 같은_회원이_동시에_설문을_제출해도_한_행만_저장된다() throws InterruptedException {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        OnboardingSurveySubmitRequest request = new OnboardingSurveySubmitRequest(
                CareerGoal.BACKEND,
                List.of(PrepStage.JOB_SEEKING),
                List.of(Category.JAVA_SPRING),
                TargetCompanyType.BIG_TECH,
                InterviewExperience.NONE,
                List.of(WeakPoint.CS),
                null
        );

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        // 스레드 풀이 작업을 순차 실행해버리면 락 없이도 통과하므로, 모든 스레드가 준비된 뒤 동시에 출발시킨다.
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    onboardingSurveyService.submitOnboardingSurvey(new MemberAuth(member.getId()), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();

        boolean finished = doneLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isZero();
        assertThat(onboardingSurveyRepository.count()).isEqualTo(1);
    }

    @Test
    void 서로_다른_회원이_동시에_제출하면_각자_한_행씩_저장된다() throws InterruptedException {
        // given
        int memberCount = 5;
        List<Member> members = memberRepository.saveAll(IntStream.range(0, memberCount)
                .mapToObj(i -> MemberFixtureBuilder.builder().nickname("회원" + i).build())
                .toList());
        OnboardingSurveySubmitRequest request = new OnboardingSurveySubmitRequest(
                CareerGoal.FRONTEND,
                List.of(PrepStage.GRADUATING),
                List.of(Category.REACT),
                TargetCompanyType.STARTUP,
                InterviewExperience.ONE_TO_THREE,
                List.of(WeakPoint.MENTAL),
                null
        );

        ExecutorService executorService = Executors.newFixedThreadPool(memberCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(memberCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (Member member : members) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    onboardingSurveyService.submitOnboardingSurvey(new MemberAuth(member.getId()), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();

        boolean finished = doneLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(memberCount);
        assertThat(failCount.get()).isZero();
        assertThat(onboardingSurveyRepository.count()).isEqualTo(memberCount);
    }

    @Test
    void 설문을_제출하면_생성일시와_수정일시가_기록된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        OnboardingSurveySubmitRequest request = new OnboardingSurveySubmitRequest(
                CareerGoal.BACKEND,
                List.of(PrepStage.JOB_SEEKING),
                List.of(Category.JAVA_SPRING),
                TargetCompanyType.BIG_TECH,
                InterviewExperience.NONE,
                List.of(WeakPoint.CS),
                null
        );

        // when
        onboardingSurveyService.submitOnboardingSurvey(new MemberAuth(member.getId()), request);

        // then
        OnboardingSurvey onboardingSurvey = onboardingSurveyRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(onboardingSurvey.getCreatedAt()).isNotNull();
        assertThat(onboardingSurvey.getUpdatedAt()).isNotNull();
    }
}
