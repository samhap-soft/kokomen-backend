package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.willThrow;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
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
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
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
        String lockKey = lockKeyOf(member);
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

    // 1번 기준 질문이 없는 문제라 락을 잡은 뒤 완료 처리에서 실패한다.
    @Test
    void 락_안에서_실패해도_진행_락을_풀고_단계를_되돌린다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problemWithoutQuestions = problemRepository.save(
                SystemDesignProblemFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member, problemWithoutQuestions,
                SystemDesignStage.REQUIREMENTS);
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(interviewId,
                new MemberAuth(member.getId())))
                .isInstanceOf(IllegalStateException.class);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(questionRepository.count()).isZero()
        );
    }

    @Test
    void 락_해제가_실패해도_요구사항_정리_결과를_돌려준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when
        SystemDesignRequirementsCompleteResponse response = systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(member.getId()));

        // then
        assertAll(
                () -> assertThat(response.stage()).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(response.curQuestionId()).isEqualTo(1),
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL)
        );
    }

    // 스펙의 검사 순서(소유자 → 단계 → 진행 락)대로 단계 오류가 락 충돌보다 먼저다.
    @Test
    void 다른_요청이_진행_락을_쥐고_있어도_단계가_틀리면_단계_오류를_먼저_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(member.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 설계 단계예요.");
        assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request");
    }

    private double sumRootQuestionMetric() {
        return meterRegistry.find(ROOT_QUESTION_METRIC_NAME).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private SystemDesignInterview saveSystemDesignInterview(Member member, SystemDesignProblem problem,
                                                            SystemDesignStage stage) {
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
        return systemDesignInterviewRepository.save(SystemDesignInterviewFixtureBuilder.builder()
                .interview(interview)
                .problem(problem)
                .stage(stage)
                .build());
    }

    private SystemDesignStage readStage(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterviewRepository.findById(systemDesignInterview.getId()).orElseThrow().getStage();
    }

    private static String lockKeyOf(Member member) {
        return InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
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
