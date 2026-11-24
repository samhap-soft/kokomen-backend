package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.fixture.async.CallerRunsTaskExecutor;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignAnswerEvaluation;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignEndEvaluation;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.service.InterviewProceedFacadeService;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService.SystemDesignAnswerDraft;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 워커를 손으로 조립해 실행기를 바꿔 끼운다. 기본은 제출한 작업을 호출 스레드에서 바로 실행하는 실행기라, submit이 돌아오면
 * 채점·저장·상태 기록·락 해제가 모두 끝나 있다. LLM 클라이언트는 BaseTest의 목이다.
 */
class SystemDesignAnswerAsyncServiceTest extends BaseTest {

    private static final String ANSWER_CONTENT = "API 서버 앞에 Redis 캐시를 두고, 캐시에 없을 때만 DB를 읽어요.";
    private static final SystemDesignDiagram SUBMITTED_DIAGRAM = SystemDesignDiagramFixture.withCache();
    private static final String REQUEST_ID_KEY = "requestId";

    @Autowired
    private SystemDesignAnswerService answerService;
    @Autowired
    private SystemDesignProceedStateStore stateStore;
    @Autowired
    private SystemDesignProceedLock proceedLock;
    @Autowired
    private SystemDesignInterviewTestSupport interviewTestSupport;
    @Autowired
    private TokenFacadeService tokenFacadeService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignQuestionRepository questionRepository;
    @Autowired
    private SystemDesignAnswerRepository answerRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;

    private SystemDesignAnswerAsyncService asyncService;

    // 질문 PK가 순번과, 면접 id가 질문 순번(1~6)과 우연히 같아지지 않게 다른 회원의 기록을 먼저 둔다.
    // 상태 키를 PK나 다른 id로 만들면 순번으로 찾는 단정이 실패한다.
    @BeforeEach
    void setUp() {
        interviewTestSupport.saveOtherMemberRecords();
        asyncService = assemble(new CallerRunsTaskExecutor());
    }

    @Test
    void 채점에_성공하면_COMPLETED이고_락을_푼다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        given(systemDesignAnswerBedrockClient.evaluateAnswer(draft.answerCommand()))
                .willReturn(new SystemDesignAnswerEvaluation(A, "단축 키 충돌 처리가 분명해요.", "다듬은 3번 질문이에요."));
        String lockValue = proceedLock.acquire(member.getId());

        // when
        asyncService.submit(draft, lockValue);

        // then
        assertAll(
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2))
                        .contains(InterviewProceedState.COMPLETED),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(readAnswer(systemDesignInterview, 2).getAnswerRank()).isEqualTo(A),
                () -> assertThat(readQuestion(systemDesignInterview, 3).getContent()).isEqualTo("다듬은 3번 질문이에요."),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateEnd(any())
        );
    }

    @Test
    void 여섯_번째_답변은_L3를_부르고_면접을_끝낸다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, B, C, A, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 6);
        given(systemDesignAnswerBedrockClient.evaluateEnd(draft.endCommand())).willReturn(endEvaluation());
        String lockValue = proceedLock.acquire(member.getId());

        // when
        asyncService.submit(draft, lockValue);

        // then
        assertAll(
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 6))
                        .contains(InterviewProceedState.COMPLETED),
                () -> assertThat(readInterviewState(systemDesignInterview)).isEqualTo(InterviewState.FINISHED),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getReportScore()).isNotNull(),
                () -> assertThat(readAnswer(systemDesignInterview, 6).getAnswerRank()).isEqualTo(A),
                () -> assertThat(readTokenCount(member)).isZero(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any())
        );
    }

    @Test
    void LLM이_실패하면_LLM_FAILED이고_저장도_차감도_없으며_락을_푼다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any()))
                .willThrow(new ExternalApiException("시스템 설계 답변 평가 호출에 실패했습니다."));
        String lockValue = proceedLock.acquire(member.getId());

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertNothingSaved(systemDesignInterview, member, 6);
        assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2)).contains(InterviewProceedState.LLM_FAILED);
    }

    @Test
    void LLM_호출에서_예상하지_못한_예외가_나도_LLM_FAILED이고_락을_푼다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any())).willThrow(new IllegalStateException("예상하지 못한 오류"));
        String lockValue = proceedLock.acquire(member.getId());

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertNothingSaved(systemDesignInterview, member, 6);
        assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2)).contains(InterviewProceedState.LLM_FAILED);
    }

    // 답변 전 검증과 차감 사이에 다른 탭이 마지막 토큰을 써 버린 경우다.
    @Test
    void 차감할_때_토큰이_모자라면_LLM_FAILED이고_아무것도_남지_않는다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        willAnswer(invocation -> {
            tokenFacadeService.useTokens(member.getId(), 1);
            return new SystemDesignAnswerEvaluation(A, "단축 키 충돌 처리가 분명해요.", "다듬은 3번 질문이에요.");
        }).given(systemDesignAnswerBedrockClient).evaluateAnswer(any());
        String lockValue = proceedLock.acquire(member.getId());

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertNothingSaved(systemDesignInterview, member, 0);
        assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2)).contains(InterviewProceedState.LLM_FAILED);
    }

    @Test
    void 마지막_답변을_차감할_때_토큰이_모자라면_LLM_FAILED이고_면접이_끝나지_않는다() {
        // given
        Member member = saveMemberWithTokens(1);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member,
                List.of(B, B, C, A, B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 6);
        willAnswer(invocation -> {
            tokenFacadeService.useTokens(member.getId(), 1);
            return endEvaluation();
        }).given(systemDesignAnswerBedrockClient).evaluateEnd(any());
        String lockValue = proceedLock.acquire(member.getId());

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertAll(
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 6))
                        .contains(InterviewProceedState.LLM_FAILED),
                () -> assertThat(readInterviewState(systemDesignInterview)).isEqualTo(InterviewState.IN_PROGRESS),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getReportScore()).isNull(),
                () -> assertThat(answerRepository.findByQuestion(readQuestion(systemDesignInterview, 6))).isEmpty(),
                () -> assertThat(readMemberScore(member)).isZero(),
                () -> assertThat(readTokenCount(member)).isZero(),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    @Test
    void 실행기가_거절하면_LLM_FAILED이고_락을_풀고_예외를_던지지_않는다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        SystemDesignAnswerAsyncService rejectingService = assemble(new RejectingTaskExecutor());
        String lockValue = proceedLock.acquire(member.getId());

        // when & then
        assertThatCode(() -> rejectingService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertNothingSaved(systemDesignInterview, member, 6);
        assertAll(
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2))
                        .contains(InterviewProceedState.LLM_FAILED),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any())
        );
    }

    // 기존 V2는 LLM_PENDING 기록이 try 밖이라 이때 락이 TTL(5분)까지 남는다.
    @Test
    void LLM_PENDING_기록이_실패해도_락을_풀고_예외를_던지지_않는다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        String lockValue = proceedLock.acquire(member.getId());
        String stateKey = SystemDesignProceedStateStore.createKey(interviewIdOf(systemDesignInterview), 2);
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getBucket(stateKey, StringCodec.INSTANCE);

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertNothingSaved(systemDesignInterview, member, 6);
        assertAll(
                () -> assertThat(redisTemplate.hasKey(stateKey)).isFalse(),
                () -> verify(systemDesignAnswerBedrockClient, never()).evaluateAnswer(any())
        );
    }

    @Test
    void 락_해제가_실패해도_상태는_COMPLETED다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any()))
                .willReturn(new SystemDesignAnswerEvaluation(A, "단축 키 충돌 처리가 분명해요.", null));
        String lockValue = proceedLock.acquire(member.getId());
        willThrow(new IllegalStateException("Redis 연결이 끊겼어요.")).given(redissonClient)
                .getScript(StringCodec.INSTANCE);

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertAll(
                () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2))
                        .contains(InterviewProceedState.COMPLETED),
                () -> assertThat(readAnswer(systemDesignInterview, 2).getAnswerRank()).isEqualTo(A),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                // 해제가 실제로 실패해 키가 남아 있다. 남은 키는 TTL이 지나면 사라진다.
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).contains(lockValue)
        );
    }

    // 저장·차감을 커밋한 뒤 COMPLETED 기록만 실패한 경우다. 폴링은 DB를 먼저 보므로 저장된 답변을 COMPLETED로 준다.
    @Test
    void 완료_기록이_실패해도_답변은_저장돼_있고_락을_푼다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        Long interviewId = interviewIdOf(systemDesignInterview);
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        given(systemDesignAnswerBedrockClient.evaluateAnswer(any()))
                .willReturn(new SystemDesignAnswerEvaluation(A, "단축 키 충돌 처리가 분명해요.", null));
        String lockValue = proceedLock.acquire(member.getId());
        String stateKey = SystemDesignProceedStateStore.createKey(interviewId, 2);
        AtomicInteger stateWrites = new AtomicInteger();
        willAnswer(invocation -> {
            if (stateWrites.getAndIncrement() == 0) {
                return invocation.callRealMethod();
            }
            throw new IllegalStateException("Redis 연결이 끊겼어요.");
        }).given(redissonClient).getBucket(stateKey, StringCodec.INSTANCE);

        // when & then
        assertThatCode(() -> asyncService.submit(draft, lockValue)).doesNotThrowAnyException();
        assertAll(
                () -> assertThat(stateWrites).hasValueGreaterThan(1),
                () -> assertThat(readAnswer(systemDesignInterview, 2).getAnswerRank()).isEqualTo(A),
                () -> assertThat(readTokenCount(member)).isEqualTo(5),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty(),
                () -> assertThat(answerService.findAnswerResult(interviewId, member.getId(), 2))
                        .isInstanceOf(SystemDesignAnswerCompletedResponse.class)
        );
    }

    @Test
    void 워커는_제출한_스레드의_MDC를_이어_쓰고_끝나면_지운다() {
        // given
        Member member = saveMemberWithTokens(6);
        SystemDesignInterview systemDesignInterview = interviewTestSupport.saveInterviewWithAnswers(member, List.of(B));
        SystemDesignAnswerDraft draft = prepare(systemDesignInterview, member, 2);
        AtomicReference<String> requestIdInWorker = new AtomicReference<>();
        AtomicReference<String> leftoverInWorker = new AtomicReference<>();
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        willAnswer(invocation -> {
            requestIdInWorker.set(MDC.get(REQUEST_ID_KEY));
            leftoverInWorker.set(MDC.get(SeparateThreadTaskExecutor.LEFTOVER_KEY));
            workerThread.set(Thread.currentThread());
            return new SystemDesignAnswerEvaluation(A, "단축 키 충돌 처리가 분명해요.", null);
        }).given(systemDesignAnswerBedrockClient).evaluateAnswer(any());
        SeparateThreadTaskExecutor executor = new SeparateThreadTaskExecutor();
        SystemDesignAnswerAsyncService separateThreadService = assemble(executor);
        String lockValue = proceedLock.acquire(member.getId());
        MDC.put(REQUEST_ID_KEY, "request-17");
        try {
            // when
            separateThreadService.submit(draft, lockValue);

            // then
            assertAll(
                    () -> assertThat(workerThread.get()).isNotNull().isNotSameAs(Thread.currentThread()),
                    () -> assertThat(requestIdInWorker.get()).isEqualTo("request-17"),
                    () -> assertThat(leftoverInWorker.get()).isNull(),
                    () -> assertThat(executor.mdcAfterTask()).isNullOrEmpty(),
                    () -> assertThat(MDC.get(REQUEST_ID_KEY)).isEqualTo("request-17"),
                    () -> assertThat(stateStore.find(interviewIdOf(systemDesignInterview), 2))
                            .contains(InterviewProceedState.COMPLETED)
            );
        } finally {
            MDC.clear();
        }
    }

    private SystemDesignAnswerAsyncService assemble(ThreadPoolTaskExecutor executor) {
        return new SystemDesignAnswerAsyncService(answerService, systemDesignAnswerBedrockClient, stateStore,
                proceedLock, executor);
    }

    private SystemDesignAnswerDraft prepare(SystemDesignInterview systemDesignInterview, Member member,
                                            int questionOrder) {
        return answerService.prepare(interviewIdOf(systemDesignInterview), member.getId(), questionOrder,
                ANSWER_CONTENT, SUBMITTED_DIAGRAM);
    }

    private static SystemDesignEndEvaluation endEvaluation() {
        Map<String, Boolean> metByPointCode = new LinkedHashMap<>();
        for (EvaluationPoint point : SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS) {
            metByPointCode.put(point.code(), true);
        }
        return new SystemDesignEndEvaluation(A, "301과 302를 근거와 함께 비교했어요.", "강점: 근거가 분명해요.", metByPointCode);
    }

    // 1번 답변만 저장된 면접에 2번 답변을 낸 경우: 답변·다음 질문·단계·다이어그램·토큰이 그대로이고 락이 풀려 있다.
    private void assertNothingSaved(SystemDesignInterview systemDesignInterview, Member member,
                                    int expectedTokenCount) {
        assertAll(
                () -> assertThat(answerRepository.findByQuestionIn(readQuestions(systemDesignInterview))).hasSize(1),
                () -> assertThat(readQuestions(systemDesignInterview)).hasSize(2),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getStage())
                        .isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(readSystemDesignInterview(systemDesignInterview).getCurrentDiagram())
                        .isNotEqualTo(SUBMITTED_DIAGRAM),
                () -> assertThat(readTokenCount(member)).isEqualTo(expectedTokenCount),
                () -> assertThat(redisService.get(lockKeyOf(member), String.class)).isEmpty()
        );
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private static Long interviewIdOf(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterview.getInterview().getId();
    }

    private static String lockKeyOf(Member member) {
        return InterviewProceedFacadeService.createInterviewProceedLockKey(member.getId());
    }

    private List<SystemDesignQuestion> readQuestions(SystemDesignInterview systemDesignInterview) {
        return questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }

    private SystemDesignQuestion readQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return questionRepository.findBySystemDesignInterviewAndQuestionOrder(systemDesignInterview, questionOrder)
                .orElseThrow();
    }

    private SystemDesignAnswer readAnswer(SystemDesignInterview systemDesignInterview, int questionOrder) {
        return answerRepository.findByQuestion(readQuestion(systemDesignInterview, questionOrder)).orElseThrow();
    }

    private SystemDesignInterview readSystemDesignInterview(SystemDesignInterview systemDesignInterview) {
        return systemDesignInterviewRepository.findById(systemDesignInterview.getId()).orElseThrow();
    }

    private InterviewState readInterviewState(SystemDesignInterview systemDesignInterview) {
        return interviewRepository.findById(interviewIdOf(systemDesignInterview)).orElseThrow().getInterviewState();
    }

    private int readTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow().getTokenCount();
    }

    private int readMemberScore(Member member) {
        return memberRepository.findById(member.getId()).orElseThrow().getScore();
    }

    // 큐가 가득 찬 실행기처럼 제출을 거절한다.
    private static class RejectingTaskExecutor extends ThreadPoolTaskExecutor {

        @Override
        public void execute(Runnable task) {
            throw new TaskRejectedException("작업 큐가 가득 찼습니다.");
        }
    }

    /**
     * 제출한 작업을 새 스레드에서 실행하고 끝날 때까지 기다린다. 풀의 스레드처럼 이전 작업이 남긴 MDC 값을 먼저 넣어 두고,
     * 작업이 끝난 뒤 그 스레드의 MDC를 남긴다.
     */
    private static class SeparateThreadTaskExecutor extends ThreadPoolTaskExecutor {

        private static final String LEFTOVER_KEY = "leftover";

        private final AtomicReference<Map<String, String>> mdcAfterTask = new AtomicReference<>();

        @Override
        public void execute(Runnable task) {
            Thread worker = new Thread(() -> {
                MDC.put(LEFTOVER_KEY, "이전 작업의 값");
                task.run();
                mdcAfterTask.set(MDC.getCopyOfContextMap());
            });
            worker.start();
            try {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        private Map<String, String> mdcAfterTask() {
            return mdcAfterTask.get();
        }
    }
}
