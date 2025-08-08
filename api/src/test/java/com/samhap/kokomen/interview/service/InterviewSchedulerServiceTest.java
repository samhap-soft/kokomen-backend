package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewSchedulerServiceTest extends BaseTest {

    @Autowired
    private InterviewSchedulerService interviewSchedulerService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void batchUpdateInterviewViewCount() {
        // given
        int interviewCount = 5;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        for (int i = 0; i < interviewCount; i++) {
            interviewRepository.save(
                    InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
        }

        for (long interviewId = 1; interviewId <= interviewCount; interviewId++) {
            String viewCount = String.valueOf(interviewId);
            redisService.setIfAbsent(InterviewViewCountService.INTERVIEW_VIEW_COUNT_KEY_PREFIX + interviewId, viewCount, Duration.ofDays(1));
        }

        // when
        interviewSchedulerService.syncInterviewViewCounts();

        // then
        for (long interviewId = 1; interviewId <= interviewCount; interviewId++) {
            Long viewCount = interviewRepository.findById(interviewId).get().getViewCount();
            assertThat(viewCount).isEqualTo(interviewId);
        }
    }
}
