package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.ServiceUnavailableException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignInterviewService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignInterviewFacadeServiceTest extends BaseTest {

    private static final String ROOT_QUESTION_METRIC_NAME = "root_question_interview_count";

    @Autowired
    private SystemDesignInterviewFacadeService systemDesignInterviewFacadeService;
    @Autowired
    private SystemDesignInterviewService systemDesignInterviewService;
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
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private SystemDesignLlmBulkhead llmBulkhead;
    @Autowired
    private RedisService redisService;
    @Autowired
    private MeterRegistry meterRegistry;

    // TRUNCATE로 테이블마다 id가 1부터 다시 시작해도 interview와 system_design_interview의 id가 같아지지 않게
    // 다른 회원의 일반 면접을 먼저 둔다. interview id 대신 system_design_interview id로 찾는 실수가 드러난다.
    // 출제하지 않는(INACTIVE) 문제도 하나 먼저 두어 문제 id와 system_design_interview id도 어긋나게 한다.
    @BeforeEach
    void setUpOtherMemberInterview() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(otherMember)
                .rootQuestion(rootQuestion)
                .build());
    }

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
                () -> assertThat(readStage(systemDesignInterview)).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isPresent()
        );
    }

    // 요구사항 정리 완료는 소유자 → 단계 → 진행 락 순서로 검사하므로 락이 잡혀 있어도 단계 오류가 먼저다.
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

    @Test
    void 다른_회원의_면접이면_단계가_틀려도_403을_먼저_준다() {
        // given
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(owner,
                problemTestSupport.saveProblem(), SystemDesignStage.HIGH_LEVEL);

        // when & then
        assertThatThrownBy(() -> systemDesignInterviewFacadeService.completeRequirements(
                systemDesignInterview.getInterview().getId(), new MemberAuth(otherMember.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 면접만 볼 수 있어요.");
    }

    @Test
    void 다른_요청이_진행_락을_쥐고_있으면_409이고_락은_그대로다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 역질문은 소유자 → 단계 → 길이 → 개수를 진행 락보다 먼저 본다. 락을 기다리게 하지 않고 흔한 오류를 바로 알려 준다.
    @Test
    void 역질문은_다른_요청이_진행_락을_쥐고_있어도_단계_길이_개수_오류를_먼저_준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview designStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview requirementStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview fullInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order <= SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(fullInterview, order);
        }
        redisService.acquireLockWithValue(lockKeyOf(member), "other-request", Duration.ofMinutes(5));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> askRequirementQuestion(designStageInterview, member, "하루에 몇 개나요?"))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요."),
                () -> assertThatThrownBy(() -> askRequirementQuestion(requirementStageInterview, member,
                        "가".repeat(201)))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문은 1~200자로 적어 주세요."),
                () -> assertThatThrownBy(() -> askRequirementQuestion(fullInterview, member, "하루에 몇 개나요?"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요."),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains("other-request"),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any())
        );
    }

    // 사전 검사와 락 획득 사이에 요구사항 정리가 끝나면 락 안에서 다시 준비하다 409로 실패한다. 이때도 락을 풀어야 한다.
    @Test
    void 락을_잡는_사이에_요구사항_정리가_끝나면_락_안에서_409이고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        Long interviewId = systemDesignInterview.getInterview().getId();
        AtomicBoolean completedBeforeLock = new AtomicBoolean();
        willAnswer(invocation -> {
            if (completedBeforeLock.compareAndSet(false, true)) {
                systemDesignInterviewService.completeRequirements(interviewId, member.getId());
            }
            return invocation.callRealMethod();
        }).given(redissonClient).getBucket(lockKeyOf(member), StringCodec.INSTANCE);

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("요구사항 단계가 끝났어요.");
        assertAll(
                () -> assertThat(completedBeforeLock).isTrue(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 클라이언트는 전송 실패와 응답 검증 실패를 모두 ExternalApiException(내부 문구)으로 올린다. 사용자에게는 정해진 문구만 보인다.
    @Test
    void LLM이_실패하면_사용자_문구로_500이고_저장하지_않으며_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExternalApiException llmFailure = new ExternalApiException(
                "시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=requirement_daily_creation_volume");
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willThrow(llmFailure);

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessage("질문에 답하지 못했어요. 잠시 후 다시 시도해 주세요.")
                .hasCause(llmFailure);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)
        );
    }

    @Test
    void LLM_호출에서_예상하지_못한_예외가_나도_저장하지_않고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any()))
                .willThrow(new IllegalStateException("예상하지 못한 오류"));

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                .isInstanceOf(IllegalStateException.class);
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
        );
    }

    // 앱의 503(global.exception.ServiceUnavailableException)은 LLM 실패(500)로 바꾸지 않고 그대로 내보낸다.
    @Test
    void 동시_실행_허가를_못_받으면_503이고_500으로_바뀌지_않으며_락을_푼다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExecutorService executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> occupants = new ArrayList<>();
        try {
            for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
                occupants.add(executor.submit(() -> llmBulkhead.execute(() -> {
                    acquired.countDown();
                    return awaitQuietly(release);
                })));
            }
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?"))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .isNotInstanceOf(ExternalApiException.class)
                    .hasMessage("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요.");
            assertAll(
                    () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                    () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                    () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty()
            );
        } finally {
            release.countDown();
            for (Future<?> occupant : occupants) {
                occupant.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
        assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
    }

    // 허가를 기다리는 요청 스레드가 인터럽트되어도(예: 강제 종료) 503으로 끝나고 진행 락을 풀며 인터럽트 플래그를 남긴다.
    @Test
    void 동시_실행_허가를_기다리다_인터럽트되면_503이고_락을_푼다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        ExecutorService executor = Executors.newFixedThreadPool(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch acquired = new CountDownLatch(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> occupants = new ArrayList<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfterThrow = new AtomicBoolean();
        try {
            for (int i = 0; i < SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS; i++) {
                occupants.add(executor.submit(() -> llmBulkhead.execute(() -> {
                    acquired.countDown();
                    return awaitQuietly(release);
                })));
            }
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            Thread requestThread = new Thread(() -> {
                try {
                    askRequirementQuestion(systemDesignInterview, member, "하루에 몇 개나 만들어지나요?");
                } catch (RuntimeException e) {
                    thrown.set(e);
                    interruptedAfterThrow.set(Thread.currentThread().isInterrupted());
                }
            });
            requestThread.start();
            // 진행 락을 잡은 뒤 허가를 기다리는 중(TIMED_WAITING)일 때 인터럽트한다.
            await().atMost(Duration.ofSeconds(2))
                    .pollInterval(Duration.ofMillis(20))
                    .until(() -> redisService.get(lockKeyOf(member), String.class).isPresent()
                            && requestThread.getState() == Thread.State.TIMED_WAITING);

            // when
            requestThread.interrupt();
            requestThread.join(Duration.ofSeconds(5).toMillis());
        } finally {
            release.countDown();
            for (Future<?> occupant : occupants) {
                occupant.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }

        // then
        assertAll(
                () -> assertThat(thrown.get()).isInstanceOf(ServiceUnavailableException.class)
                        .hasMessage("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요."),
                () -> assertThat(interruptedAfterThrow).isTrue(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignRequirementBedrockClient, never()).answerRequirement(any()),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty(),
                () -> assertThat(llmBulkhead.availablePermits()).isEqualTo(SystemDesignLlmBulkhead.MAX_CONCURRENT_CALLS)
        );
    }

    @Test
    void 락_해제가_실패해도_역질문_답을_돌려준다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willReturn(
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when
        SystemDesignRequirementAnswerResponse response = askRequirementQuestion(systemDesignInterview, member,
                "하루에 몇 개나 만들어지나요?");

        // then
        assertAll(
                () -> assertThat(response.answer()).isEqualTo("하루 평균 10만 개 정도예요."),
                () -> assertThat(response.remainingCount()).isEqualTo(4),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isPresent()
        );
    }

    // 락 안에서 실패해도(저장 직전 개수 재확인) 락을 풀고 행을 남기지 않는다.
    @Test
    void LLM을_기다리는_동안_5번째_역질문이_저장되면_400이고_락을_푼다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order < SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order);
        }
        given(systemDesignRequirementBedrockClient.answerRequirement(any())).willAnswer(invocation -> {
            saveRequirementQuestion(systemDesignInterview, SystemDesignRequirementQuestion.MAX_COUNT);
            return new SystemDesignRequirementAnswerResult(List.of(), "");
        });

        // when & then
        assertThatThrownBy(() -> askRequirementQuestion(systemDesignInterview, member, "다섯 번째 역질문"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("역질문은 5개까지 할 수 있어요.");
        assertAll(
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .hasSize(SystemDesignRequirementQuestion.MAX_COUNT)
                        .extracting(SystemDesignRequirementQuestion::getContent)
                        .doesNotContain("다섯 번째 역질문")
        );
    }

    private SystemDesignRequirementAnswerResponse askRequirementQuestion(SystemDesignInterview systemDesignInterview,
                                                                         Member member, String question) {
        return systemDesignInterviewFacadeService.askRequirementQuestion(systemDesignInterview.getInterview().getId(),
                new SystemDesignRequirementQuestionRequest(question), new MemberAuth(member.getId()));
    }

    private void saveRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .content("역질문 " + questionOrder)
                .build());
    }

    private List<SystemDesignRequirementQuestion> readRequirementQuestions(
            SystemDesignInterview systemDesignInterview) {
        return requirementQuestionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    private static Object awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
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
