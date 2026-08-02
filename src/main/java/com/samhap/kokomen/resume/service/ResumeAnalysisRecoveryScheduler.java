package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 워커가 죽어 종단 상태에 도달하지 못한 이력서 분석 행을 회수한다.
 *
 * <p>이 스케줄러는 안전망이 아니라 설계의 구성 요소다. {@code resumeAnalysisExecutor}는 종료 시 대기 큐를
 * 의도적으로 버리고, 워커는 어느 문장에서든 사라질 수 있다. 워커가 스스로 완주를 보장하지 않아도 되는 근거가
 * 바로 이 스윕이며, 그래서 워커가 남길 수 있는 모든 중간 상태는 여기서 종단 가능해야 한다.
 * 남는 중간 상태는 두 가지뿐이다 — 평가 커밋 전의 {@code PENDING}과 질문 커밋 전의
 * {@code EVALUATION_COMPLETED}. 나머지 분기는 워커가 직접 종단 상태를 찍는다.
 *
 * <p>재구동은 하지 않는다. LLM 콜을 다시 태우면 중복 비용이 들고, 살아 있는 워커와 겹쳐 이중 실행이 된다.
 * 재실행은 사용자가 명시적으로 요청하는 질문 재생성 경로로만 일어난다.
 *
 * <p>행 루프를 {@code ResumeAnalysisStateService}가 아니라 이 클래스에 두는 이유는 자기 호출이다.
 * 같은 빈 안에서 루프가 전이 메서드를 부르면 프록시를 거치지 않아 {@code REQUIRES_NEW}가 적용되지 않고,
 * 행별 독립 트랜잭션이 사라진다. 루프 자체가 무트랜잭션이면 {@code findByIdForUpdate}는
 * {@code TransactionRequiredException}으로 죽는다.
 *
 * <p>결제 복구 스케줄러와 달리 행별 락을 쓰지 않고 인스턴스 전역 락 하나만 쓴다. 결제 복구는 외부 결제 API를
 * 다시 부르므로 중복 실행 자체가 부작용이지만, 여기서 하는 일은 조건부 상태 전이({@code PESSIMISTIC_WRITE} +
 * 상태 가드)와 CAS 멱등인 회수 과금뿐이다. 두 인스턴스가 같은 행을 동시에 집어도 한쪽은 상태 가드에서 멈추고
 * 과금은 한 번만 성사되므로 결과가 같다. 전역 락은 정확성 장치가 아니라 같은 구간을 두 인스턴스가 동시에
 * 훑는 낭비를 줄이는 장치다. 그래서 회차가 끝나면 성공이든 실패든 곧바로 해제하고, TTL은 실행 중 프로세스가
 * 죽었을 때 락이 영구히 남지 않게 하는 상한으로만 둔다. 해제는 Lua CAS인 {@code releaseLockSafely}로 해서,
 * 자기 락이 TTL로 이미 만료된 뒤라면 다른 인스턴스가 새로 건 락을 지우지 않는다.
 *
 * <p>{@code STALE_THRESHOLD}는 파사드가 회원의 중복 제출을 막는 진행 중 판정 창보다 짧게 둔다. 그 창은
 * 스스로 시간 제한이 있어 고착된 행이 회원을 영구히 차단하지는 않는다 — 짧게 두는 이유는 그 창이 조용히
 * 만료되기 전에 행이 종단 상태를 얻어, 사용자가 진행 중이 아니라 확정된 실패를 보게 하는 것이다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ResumeAnalysisRecoveryScheduler {

    public static final String SWEEP_LOCK_KEY = "lock:resume-analysis:sweep:scheduler";
    public static final Duration SWEEP_LOCK_TTL = Duration.ofMinutes(4);
    public static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    public static final int MAX_SWEEP_COUNT = 200;

    private final RedisService redisService;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisStateService resumeAnalysisStateService;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void sweepStaleAnalyses() {
        String lockValue = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithValue(SWEEP_LOCK_KEY, lockValue, SWEEP_LOCK_TTL)) {
            log.debug("이력서 분석 잔류 정리 스킵 - 다른 인스턴스가 실행 중");
            return;
        }

        try {
            LocalDateTime threshold = LocalDateTime.now().minus(STALE_THRESHOLD);
            int pending = sweepStalePending(threshold);
            int questionStage = sweepStaleQuestionStage(threshold);
            if (pending >= MAX_SWEEP_COUNT || questionStage >= MAX_SWEEP_COUNT) {
                log.warn("이력서 분석 잔류 정리 상한 도달 - pending: {}, questionStage: {}", pending, questionStage);
            }
        } catch (Exception e) {
            log.error("이력서 분석 잔류 행 정리 실패", e);
        } finally {
            redisService.releaseLockSafely(SWEEP_LOCK_KEY, lockValue);
        }
    }

    private int sweepStalePending(LocalDateTime threshold) {
        List<ResumeAnalysis> staleAnalyses = resumeAnalysisRepository.findByStateAndCreatedAtBefore(
                ResumeAnalysisState.PENDING, threshold, PageRequest.of(0, MAX_SWEEP_COUNT));
        for (ResumeAnalysis staleAnalysis : staleAnalyses) {
            failEvaluationQuietly(staleAnalysis.getId());
        }
        return staleAnalyses.size();
    }

    // 한 행이 실패해도 나머지 행은 계속 종단시킨다. 예외를 올리면 이번 회차의 남은 행이 통째로 남는다.
    private void failEvaluationQuietly(Long analysisId) {
        try {
            resumeAnalysisStateService.failEvaluation(analysisId, ResumeAnalysisFailureReason.STALE_SWEEP);
        } catch (Exception e) {
            log.error("이력서 분석 잔류 평가 단계 종단 실패 - analysisId: {}", analysisId, e);
        }
    }

    /**
     * 질문 단계의 고착 판정은 {@code created_at}이 아니라 {@code question_started_at}으로 한다.
     * {@code created_at}으로 판정하면 평가에 오래 걸린 정상 요청이 질문 콜 도중에 실패로 찍히고, 한참 뒤의
     * 사용자 재시도도 즉시 스윕 대상이 되어 정상 생성한 질문이 상태 가드에 폐기되면서 재시도 횟수만 소모된다.
     * 평가 커밋과 재시도 복원이 모두 이 컬럼을 함께 갱신하므로 {@code EVALUATION_COMPLETED}인 행에는
     * 항상 값이 들어 있다.
     */
    private int sweepStaleQuestionStage(LocalDateTime threshold) {
        List<ResumeAnalysis> staleAnalyses = resumeAnalysisRepository.findByStateAndQuestionStartedAtBefore(
                ResumeAnalysisState.EVALUATION_COMPLETED, threshold, PageRequest.of(0, MAX_SWEEP_COUNT));
        for (ResumeAnalysis staleAnalysis : staleAnalyses) {
            failQuestionsQuietly(staleAnalysis.getId());
        }
        return staleAnalyses.size();
    }

    /**
     * 평가가 이미 공개된 행이므로 종단과 함께 회수 과금을 한 번 더 시도한다. 평가 직후의 과금이 끊긴 채
     * 워커가 사라진 행이 무료로 끝나는 경로를 막는 것이 목적이다.
     *
     * <p>과금 주체는 엔티티의 LAZY {@code member} 프록시를 역참조해 얻지 않고 리포지토리 쿼리로
     * {@code member_id}만 받는다. 이 루프는 트랜잭션 밖이라 프록시 역참조가 끊기기 때문이다.
     * {@code chargeTokensIfNeeded}는 넘겨받은 id를 행의 소유자와 대조하지 않고 그대로 신뢰하므로,
     * 과금 주체를 정하는 책임이 전부 {@code findRecoveryBillingMemberId}에 있다. 그 쿼리가 게스트 행
     * ({@code member_id IS NULL})과 이미 과금된 행을 스스로 걸러 내고, 그래도 남는 중복 호출은
     * {@code markTokenCharged}의 CAS가 흡수한다.
     */
    private void failQuestionsQuietly(Long analysisId) {
        try {
            Long billingMemberId = resumeAnalysisRepository.findRecoveryBillingMemberId(analysisId)
                    .orElse(null);
            resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.STALE_SWEEP);
            if (billingMemberId != null) {
                resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, billingMemberId);
            }
        } catch (Exception e) {
            log.error("이력서 분석 잔류 질문 단계 종단 실패 - analysisId: {}", analysisId, e);
        }
    }
}
