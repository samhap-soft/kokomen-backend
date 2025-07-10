package com.samhap.kokomen.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterviewBatchRepositoryTest extends BaseTest {

    @Autowired
    private InterviewBatchRepository interviewBatchRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    void batchUpdateInterviewViewCount() {
        // given
        int interviewCount = 15;
        int batchSize = 3;
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());
        Map<Long, Long> interviewViewCounts = new HashMap<>();

        for (long interviewId = 1; interviewId <= interviewCount; interviewId++) {
            long viewCount = interviewId;
            interviewRepository.save(
                    InterviewFixtureBuilder.builder().member(member).rootQuestion(rootQuestion).interviewState(InterviewState.FINISHED).build());
            interviewViewCounts.put(interviewId, viewCount);

        }

        // when
        interviewBatchRepository.batchUpdateInterviewViewCount(interviewViewCounts, batchSize);

        // then
        for (long interviewId = 1; interviewId <= interviewCount; interviewId++) {
            Long viewCount = interviewRepository.findById(interviewId).get().getViewCount();
            assertThat(viewCount).isEqualTo(interviewViewCounts.get(interviewId));
        }
    }
}
