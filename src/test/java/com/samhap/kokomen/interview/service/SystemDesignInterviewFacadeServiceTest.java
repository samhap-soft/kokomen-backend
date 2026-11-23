package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignInterviewFacadeServiceTest extends BaseTest {

    private static final String ROOT_QUESTION_METRIC_NAME = "root_question_interview_count";

    @Autowired
    private SystemDesignInterviewFacadeService systemDesignInterviewFacadeService;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private RedisService redisService;
    @Autowired
    private MeterRegistry meterRegistry;

    // 루트 질문 메트릭 애스펙트는 기존 시작 파사드에만 걸린다. 시스템 설계 시작에 걸리면 루트 질문 id 조회가 null을 돌려
    // root_question_id="null" 계열이 생긴다. MeterRegistry는 캐시된 컨텍스트에서 테스트끼리 공유되므로 합계의 변화만 본다.
    @Test
    void 시스템_설계_면접_시작은_루트_질문_메트릭을_올리지_않는다() {
        // given
        Member member = saveMemberWithTokens(6);
        problemTestSupport.saveProblem();
        double before = sumRootQuestionMetric();

        // when
        systemDesignInterviewFacadeService.startInterview(new MemberAuth(member.getId()));

        // then
        assertThat(sumRootQuestionMetric()).isEqualTo(before);
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있어도_시작할_수_있다() {
        // given
        Member member = saveMemberWithTokens(6);
        problemTestSupport.saveProblem();
        String lockKey = InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
        redisService.acquireLockWithValue(lockKey, "other-request", Duration.ofMinutes(5));

        // when
        SystemDesignInterviewStartResponse response = systemDesignInterviewFacadeService.startInterview(
                new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(systemDesignInterviewRepository.findByInterviewId(response.interviewId())).isPresent(),
                () -> assertThat(redisService.get(lockKey, String.class)).contains("other-request")
        );
    }

    private double sumRootQuestionMetric() {
        return meterRegistry.find(ROOT_QUESTION_METRIC_NAME).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }
}
