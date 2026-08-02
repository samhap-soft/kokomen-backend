package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 게스트 락 키는 ResumeAnalysisFacadeService.createGuestLockKey를 통해서만 조립한다(리터럴 복제 금지).
// 같은 패키지(com.samhap.kokomen.resume.service)이므로 import하지 않는다.
class ResumeAnalysisRecoverySchedulerTest extends BaseTest {

    @Autowired
    private ResumeAnalysisRecoveryScheduler resumeAnalysisRecoveryScheduler;
    @Autowired
    private ResumeAnalysisStateService resumeAnalysisStateService;
    @MockitoSpyBean
    private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private RedisService redisService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 잔류_PENDING은_EVALUATION_FAILED로_종단된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(pendingMemberAnalysis(member, false));
        backdateCreatedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis swept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(swept.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(swept.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.STALE_SWEEP)
        );
    }

    @Test
    void 잔류_질문단계는_QUESTION_FAILED로_종단된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, false));
        backdateQuestionStartedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis swept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(swept.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(swept.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.STALE_SWEEP),
                () -> assertThat(swept.getTotalScore()).isEqualTo(78)
        );
    }

    @Test
    void 평가_직후_질문_콜_진행_중인_행은_종단되지_않는다() {
        // given — question_started_at이 방금 세팅되었다
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, false));
        backdateCreatedAtMinutes(analysis.getId(), 60);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis notSwept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(notSwept.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(notSwept.getFailureReason()).isNull()
        );
    }

    @Test
    void 재시도로_복원된_행은_즉시_종단되지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(questionFailedMemberAnalysis(member));
        backdateQuestionStartedAtMinutes(analysis.getId(), 120);
        resumeAnalysisStateService.restoreForQuestionRetry(analysis.getId());

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis restored = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(restored.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(restored.getQuestionRetryCount()).isEqualTo(1)
        );
    }

    @Test
    void sweep이_찍은_뒤_도착한_워커_결과는_폐기된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(pendingMemberAnalysis(member, false));
        backdateCreatedAtMinutes(analysis.getId(), 11);
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // when — 살아있던 워커가 뒤늦게 자기 실패를 기록하려 한다
        resumeAnalysisStateService.failEvaluation(analysis.getId(), ResumeAnalysisFailureReason.EVALUATION_LLM);

        // then
        ResumeAnalysis swept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertThat(swept.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.STALE_SWEEP);
    }

    @Test
    void 잔류_게스트_PENDING_종단시_IP_락이_해제된다() {
        // given
        String guestIp = "11.22.33.71";
        String lockKey = ResumeAnalysisFacadeService.createGuestLockKey(guestIp);
        String lockValue = UUID.randomUUID().toString();
        redisService.acquireLockWithValue(lockKey, lockValue,
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysis.forGuest(
                UUID.randomUUID().toString(), new ClientIp(guestIp), lockValue, jobInput()));
        backdateCreatedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId()).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(redisTemplate.hasKey(lockKey)).isFalse()
        );
    }

    @Test
    void 잔류_게스트_질문단계_종단시_IP_락은_유지된다() {
        // given
        String guestIp = "11.22.33.72";
        String lockKey = ResumeAnalysisFacadeService.createGuestLockKey(guestIp);
        String lockValue = UUID.randomUUID().toString();
        redisService.acquireLockWithValue(lockKey, lockValue,
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
        ResumeAnalysis saved = resumeAnalysisRepository.save(
                evaluationCompletedGuestAnalysis(guestIp, lockValue));
        backdateQuestionStartedAtMinutes(saved.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(saved.getId()).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(redisTemplate.hasKey(lockKey)).isTrue()
        );
    }

    @Test
    void 잔류_질문단계_종단시_미과금이면_회수_과금된다() {
        // given — 분석 id와 회원 id를 어긋내 인자 배선이 뒤바뀌면 드러나게 한다(분석 id 1, 회원 id 3)
        memberRepository.save(MemberFixtureBuilder.builder().build());
        memberRepository.save(MemberFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        saveTokens(member, 10, 0);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, true));
        backdateQuestionStartedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis swept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(analysis.getId()).isNotEqualTo(member.getId()),
                () -> assertThat(swept.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(swept.getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(readFreeTokenCount(member))
                        .isEqualTo(10 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void 회수_과금은_분석_소유자에게만_차감된다() {
        // given — 분석 id 1은 회원 id 1과 겹치므로, 소유자를 회원 id 3으로 두어 오배선을 드러낸다
        Member other = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member another = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member owner = memberRepository.save(MemberFixtureBuilder.builder().build());
        saveTokens(other, 10, 0);
        saveTokens(another, 10, 0);
        saveTokens(owner, 10, 0);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(owner, true));
        backdateQuestionStartedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertAll(
                () -> assertThat(readFreeTokenCount(owner))
                        .isEqualTo(10 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(readFreeTokenCount(other)).isEqualTo(10),
                () -> assertThat(readFreeTokenCount(another)).isEqualTo(10)
        );
    }

    @Test
    void 회수_과금은_이미_과금된_행을_다시_차감하지_않는다() {
        // given
        memberRepository.save(MemberFixtureBuilder.builder().build());
        memberRepository.save(MemberFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        saveTokens(member, 10, 0);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, true));
        backdateQuestionStartedAtMinutes(analysis.getId(), 11);
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // when — 같은 행을 이미 읽어 둔 두 번째 인스턴스의 회수 과금이 뒤늦게 도착한다
        resumeAnalysisStateService.chargeTokensIfNeeded(analysis.getId(), member.getId());

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId()).orElseThrow()
                        .getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(readFreeTokenCount(member))
                        .isEqualTo(10 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void 무료_사용_분석은_회수_과금하지_않는다() {
        // given — 무료 1회로 제출된 회원 분석이라 잔여 토큰이 있어도 차감 대상이 아니다
        memberRepository.save(MemberFixtureBuilder.builder().build());
        memberRepository.save(MemberFixtureBuilder.builder().build());
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        saveTokens(member, 10, 0);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, false));
        backdateQuestionStartedAtMinutes(analysis.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        ResumeAnalysis swept = resumeAnalysisRepository.findById(analysis.getId()).orElseThrow();
        assertAll(
                () -> assertThat(swept.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(swept.getChargedTokenCount()).isZero(),
                () -> assertThat(swept.isTokenChargeFailed()).isFalse(),
                () -> assertThat(readFreeTokenCount(member)).isEqualTo(10)
        );
    }

    @Test
    void 게스트_잔류_질문단계는_회수_과금하지_않는다() {
        // given
        ResumeAnalysis saved = resumeAnalysisRepository.save(
                evaluationCompletedGuestAnalysis("11.22.33.73", UUID.randomUUID().toString()));
        backdateQuestionStartedAtMinutes(saved.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertThat(resumeAnalysisRepository.findById(saved.getId()).orElseThrow().getChargedTokenCount())
                .isZero();
    }

    @Test
    void 게스트_행은_billing_required가_켜져_있어도_회수_과금_대상이_아니다() {
        // given — 과금 여부 두 조건(billing_required, charged_token_count)을 모두 통과시켜
        // 게스트라는 사실만이 제외 사유로 남게 한다
        ResumeAnalysis saved = resumeAnalysisRepository.save(
                evaluationCompletedGuestAnalysis("11.22.33.74", UUID.randomUUID().toString()));
        jdbcTemplate.update("UPDATE resume_analysis SET billing_required = TRUE WHERE id = ?", saved.getId());
        backdateQuestionStartedAtMinutes(saved.getId(), 11);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findRecoveryBillingMemberId(saved.getId())).isEmpty(),
                () -> assertThat(resumeAnalysisRepository.findById(saved.getId()).orElseThrow()
                        .getChargedTokenCount()).isZero()
        );
    }

    @Test
    void 정리_배치가_지운_잔류_PENDING_행을_만나도_나머지_행은_계속_종단된다() {
        // given — 스윕의 SELECT와 readForUpdate 사이에 정리 배치가 같은 행을 지우는 인터리빙을 재현한다.
        // 사라진 행을 결과의 첫 번째로 두어, 그 행의 예외가 루프를 끊으면 뒤의 행이 손도 못 댄 채 남게 한다.
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        List<ResumeAnalysis> selected = selectionStartingWithDeletedRow(
                saveStalePendingAnalyses(member, 3));
        doReturn(selected).when(resumeAnalysisRepository).findByStateAndCreatedAtBefore(
                eq(ResumeAnalysisState.PENDING), any(LocalDateTime.class), any(Pageable.class));

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        List<String> remainingStates = readAllStates();
        assertAll(
                () -> assertThat(remainingStates).hasSize(2),
                () -> assertThat(remainingStates).containsOnly(ResumeAnalysisState.EVALUATION_FAILED.name())
        );
    }

    @Test
    void 정리_배치가_지운_잔류_질문단계_행을_만나도_나머지_행은_계속_종단된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        List<ResumeAnalysis> selected = selectionStartingWithDeletedRow(
                saveStaleQuestionStageAnalyses(member, 3));
        doReturn(selected).when(resumeAnalysisRepository).findByStateAndQuestionStartedAtBefore(
                eq(ResumeAnalysisState.EVALUATION_COMPLETED), any(LocalDateTime.class), any(Pageable.class));

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        List<String> remainingStates = readAllStates();
        assertAll(
                () -> assertThat(remainingStates).hasSize(2),
                () -> assertThat(remainingStates).containsOnly(ResumeAnalysisState.QUESTION_FAILED.name())
        );
    }

    @Test
    void 다른_인스턴스가_스윕_락을_잡고_있으면_스윕하지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = resumeAnalysisRepository.save(pendingMemberAnalysis(member, false));
        backdateCreatedAtMinutes(analysis.getId(), 11);
        redisService.acquireLock(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY,
                ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_TTL);

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then — 남의 락을 해제하지도 않는다
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId()).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(redisTemplate.hasKey(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY)).isTrue()
        );
    }

    @Test
    void 스윕은_회차가_끝나면_락을_해제한다() {
        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then
        assertThat(redisTemplate.hasKey(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY)).isFalse();
    }

    @Test
    void 자기_락이_만료된_뒤_다른_인스턴스가_새로_건_락은_지우지_않는다() {
        // given — 스윕이 도는 사이 자기 락이 TTL로 만료되고 다른 인스턴스가 같은 키를 새로 잡은 상황
        String otherInstanceLockValue = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            redisService.releaseLock(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY);
            redisService.acquireLockWithValue(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY,
                    otherInstanceLockValue, ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_TTL);
            return List.<ResumeAnalysis>of();
        }).when(resumeAnalysisRepository).findByStateAndQuestionStartedAtBefore(
                eq(ResumeAnalysisState.EVALUATION_COMPLETED), any(LocalDateTime.class), any(Pageable.class));

        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then — 무조건 삭제였다면 남의 락이 사라진다
        assertThat(redisService.get(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY, String.class))
                .contains(otherInstanceLockValue);
    }

    @Test
    void 종단_상한_건수를_초과하지_않는다() {
        // when
        resumeAnalysisRecoveryScheduler.sweepStaleAnalyses();

        // then — 호출 횟수가 아니라 모든 호출의 Pageable을 검사한다. 백그라운드 스케줄 발화가 같은 인자로
        // 한 번 더 호출해도 결과가 바뀌지 않으므로 어느 방향으로도 흔들리지 않는다.
        ArgumentCaptor<Pageable> pendingPageables = ArgumentCaptor.forClass(Pageable.class);
        verify(resumeAnalysisRepository, atLeastOnce()).findByStateAndCreatedAtBefore(
                eq(ResumeAnalysisState.PENDING), any(LocalDateTime.class), pendingPageables.capture());
        ArgumentCaptor<Pageable> questionStagePageables = ArgumentCaptor.forClass(Pageable.class);
        verify(resumeAnalysisRepository, atLeastOnce()).findByStateAndQuestionStartedAtBefore(
                eq(ResumeAnalysisState.EVALUATION_COMPLETED), any(LocalDateTime.class),
                questionStagePageables.capture());
        assertAll(
                () -> assertThat(pendingPageables.getAllValues()).containsOnly(PageRequest.of(0, 200)),
                () -> assertThat(questionStagePageables.getAllValues()).containsOnly(PageRequest.of(0, 200))
        );
    }

    @Test
    void 스윕_스케줄_설정과_상수가_고정된다() throws NoSuchMethodException {
        // given
        Scheduled scheduled = ResumeAnalysisRecoveryScheduler.class.getDeclaredMethod("sweepStaleAnalyses")
                .getAnnotation(Scheduled.class);

        // when & then — TTL은 실행 중 급사에 대비한 상한이다(정상 종료 시에는 곧바로 해제한다)
        assertAll(
                () -> assertThat(scheduled.fixedDelay()).isEqualTo(5L),
                () -> assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.MINUTES),
                () -> assertThat(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_KEY)
                        .isEqualTo("lock:resume-analysis:sweep:scheduler"),
                () -> assertThat(ResumeAnalysisRecoveryScheduler.SWEEP_LOCK_TTL).isEqualTo(Duration.ofMinutes(4)),
                () -> assertThat(ResumeAnalysisRecoveryScheduler.STALE_THRESHOLD).isEqualTo(Duration.ofMinutes(10)),
                () -> assertThat(ResumeAnalysisRecoveryScheduler.MAX_SWEEP_COUNT).isEqualTo(200)
        );
    }

    private List<ResumeAnalysis> saveStalePendingAnalyses(Member member, int count) {
        List<ResumeAnalysis> saved = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ResumeAnalysis stale = resumeAnalysisRepository.save(pendingMemberAnalysis(member, false));
            backdateCreatedAtMinutes(stale.getId(), 11);
            saved.add(stale);
        }
        return saved;
    }

    private List<ResumeAnalysis> saveStaleQuestionStageAnalyses(Member member, int count) {
        List<ResumeAnalysis> saved = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ResumeAnalysis stale = resumeAnalysisRepository.save(evaluationCompletedMemberAnalysis(member, false));
            backdateQuestionStartedAtMinutes(stale.getId(), 11);
            saved.add(stale);
        }
        return saved;
    }

    /**
     * 첫 행만 DB에서 지운 뒤 원래 목록을 그대로 돌려준다. 스윕이 그 행을 잠그려는 순간 프로덕션 코드가
     * NotFoundException을 던지는 상태가 되고, 나머지 행은 여전히 존재한다.
     */
    private List<ResumeAnalysis> selectionStartingWithDeletedRow(List<ResumeAnalysis> saved) {
        jdbcTemplate.update("DELETE FROM resume_analysis WHERE id = ?", saved.get(0).getId());
        return saved;
    }

    private List<String> readAllStates() {
        return jdbcTemplate.queryForList("SELECT state FROM resume_analysis ORDER BY id", String.class);
    }

    private ResumeAnalysis pendingMemberAnalysis(Member member, boolean billingRequired) {
        return ResumeAnalysis.forMember(member, null, null, jobInput(), billingRequired);
    }

    private ResumeAnalysis evaluationCompletedMemberAnalysis(Member member, boolean billingRequired) {
        ResumeAnalysis analysis = pendingMemberAnalysis(member, billingRequired);
        analysis.completeEvaluation(jdAbsentEvaluation());
        return analysis;
    }

    private ResumeAnalysis questionFailedMemberAnalysis(Member member) {
        ResumeAnalysis analysis = evaluationCompletedMemberAnalysis(member, false);
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        return analysis;
    }

    private ResumeAnalysis evaluationCompletedGuestAnalysis(String guestIp, String lockValue) {
        ResumeAnalysis analysis = ResumeAnalysis.forGuest(
                UUID.randomUUID().toString(), new ClientIp(guestIp), lockValue, jobInput());
        analysis.completeEvaluation(jdAbsentEvaluation());
        return analysis;
    }

    private ResumeAnalysisJobInput jobInput() {
        return new ResumeAnalysisJobInput("백엔드 개발자", null, "신입");
    }

    // 90/80/70/60 × JD_ABSENT(0.30/0.30/0.30/0.10) = 78
    private ResumeAnalysisEvaluation jdAbsentEvaluation() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                dimension(90), dimension(80), dimension(70), dimension(60), null, null, "종합 총평");
        return evaluation.withTotalScore(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluation));
    }

    private DimensionScore dimension(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }

    private void saveTokens(Member member, int freeTokenCount, int paidTokenCount) {
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(paidTokenCount).build());
    }

    private int readFreeTokenCount(Member member) {
        return tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE)
                .orElseThrow()
                .getTokenCount();
    }

    private void backdateCreatedAtMinutes(Long analysisId, int minutes) {
        jdbcTemplate.update("UPDATE resume_analysis SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutes), analysisId);
    }

    private void backdateQuestionStartedAtMinutes(Long analysisId, int minutes) {
        jdbcTemplate.update("UPDATE resume_analysis SET question_started_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutes), analysisId);
    }
}
