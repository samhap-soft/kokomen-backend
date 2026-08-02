package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * resume_analysis의 모든 상태 전이가 통과하는 단일 관문.
 * 불변식: 전이는 (a) findByIdForUpdate(PESSIMISTIC_WRITE) + 엔티티 가드 메서드,
 * (b) WHERE id = ? AND state = ? 조건부 벌크 UPDATE + 영향 행수 판정 둘 중 하나로만 한다.
 * 락 없이 엔티티를 로드해 세터로 바꾸면 동시 claim이 member_id = NULL로 덮여 조용히 소실된다.
 *
 * <p>게스트 락 키·TTL·토큰 비용 상수는 ResumeAnalysisFacadeService에만 선언되어 있고 이 클래스는
 * 참조만 한다(같은 패키지이므로 import가 필요 없다). 여기서 재선언하지 않는다. 락 키 문자열도 직접
 * 조립하지 않고 그 클래스의 createGuestLockKey를 쓴다 — 거는 쪽과 해제하는 쪽이 같은 조립 지점을 공유해야
 * 한쪽만 바뀌었을 때 키가 어긋나지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeAnalysisStateService {

    private static final int TOKEN_CHARGE_MAX_ATTEMPTS = 3;
    private static final Duration TOKEN_CHARGE_BACKOFF = Duration.ofMillis(200);

    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisService resumeAnalysisService;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final TokenFacadeService tokenFacadeService;
    private final RedisService redisService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeEvaluation(Long analysisId, ResumeAnalysisEvaluation evaluation) {
        ResumeAnalysis analysis = readForUpdate(analysisId);
        if (analysis.getState() != ResumeAnalysisState.PENDING) {
            log.warn("이력서 분석 평가 결과 폐기 - analysisId: {}, state: {}", analysisId, analysis.getState());
            return false;
        }
        analysis.completeEvaluation(evaluation);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failEvaluation(Long analysisId, ResumeAnalysisFailureReason reason) {
        ResumeAnalysis analysis = readForUpdate(analysisId);
        if (analysis.getState() != ResumeAnalysisState.PENDING) {
            log.warn("이력서 분석 평가 실패 기록 생략 - analysisId: {}, state: {}", analysisId, analysis.getState());
            return;
        }
        analysis.failEvaluation(reason);
        releaseGuestLockIfNeeded(analysis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeQuestions(Long analysisId, List<GeneratedQuestionDto> questions) {
        ResumeAnalysis analysis = readForUpdate(analysisId);
        if (analysis.getState() != ResumeAnalysisState.EVALUATION_COMPLETED) {
            log.warn("이력서 분석 질문 결과 폐기 - analysisId: {}, state: {}", analysisId, analysis.getState());
            return false;
        }
        List<GeneratedQuestion> generatedQuestions = new ArrayList<>();
        for (int order = 0; order < questions.size(); order++) {
            GeneratedQuestionDto question = questions.get(order);
            generatedQuestions.add(
                    GeneratedQuestion.forAnalysis(analysis, question.question(), question.reason(), order));
        }
        generatedQuestionRepository.saveAll(generatedQuestions);
        analysis.completeQuestions();
        return true;
    }

    /**
     * 평가는 이미 공개됐으므로 게스트 락을 해제하지 않는다 — 비회원 1회가 소진된 것으로 확정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failQuestions(Long analysisId, ResumeAnalysisFailureReason reason) {
        ResumeAnalysis analysis = readForUpdate(analysisId);
        if (analysis.getState() != ResumeAnalysisState.EVALUATION_COMPLETED) {
            log.warn("이력서 분석 질문 실패 기록 생략 - analysisId: {}, state: {}", analysisId, analysis.getState());
            return;
        }
        analysis.failQuestions(reason);
    }

    /**
     * 재시도 중복 실행을 막는 단일 수단이 이 조건부 벌크 UPDATE의 영향 행수다.
     * @DistributedLock은 202 응답 시점에 풀려 비동기 작업을 보호하지 못한다.
     * question_started_at을 함께 갱신해 복원 직후 고착 행 스윕에 잡히지 않게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreForQuestionRetry(Long analysisId) {
        int updated = resumeAnalysisRepository.restoreForQuestionRetry(
                analysisId, ResumeAnalysis.MAX_QUESTION_RETRY, LocalDateTime.now());
        if (updated != 1) {
            throw new BadRequestException("질문 재생성이 필요한 상태가 아닙니다.");
        }
    }

    /**
     * restoreForQuestionRetry로 올린 재시도 횟수를 되돌린다. 복원 직후 질문 hop을 executor에 넘기지 못한
     * 호출자가 쓴다 — 되돌리지 않으면 한 번도 실행되지 않은 시도가 사용자의 재생성 횟수를 소모한다.
     * 조건부 전이라 그 사이 다른 주체가 행을 진전시켰다면 0행이 갱신되고, 그때는 되돌리지 않는 편이 안전하다
     * (질문 hop이 실제로 돌고 있을 수 있다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertQuestionRetry(Long analysisId, ResumeAnalysisFailureReason reason) {
        if (resumeAnalysisRepository.revertQuestionRetry(analysisId, reason) != 1) {
            log.warn("이력서 분석 재시도 복원 되돌리기 생략 - analysisId: {}", analysisId);
        }
    }

    /**
     * 트랜잭션을 걸지 않는다. CAS와 실패 기록은 각각 REQUIRES_NEW로 커밋되고, 백오프 sleep이
     * 트랜잭션을 붙잡고 있어서는 안 된다.
     *
     * <p>평가가 공개된 뒤의 모든 종단 지점에서 반복 호출된다: 평가 커밋 직후, 질문 hop 종단(완료·실패),
     * 그리고 고착 행을 종단시키는 스케줄러의 EVALUATION_COMPLETED → QUESTION_FAILED 전이.
     * CAS가 멱등을 보장하므로 중복 차감이 없다.
     */
    public void chargeTokensIfNeeded(Long analysisId, Long billingMemberId) {
        if (billingMemberId == null) {
            return;
        }
        if (!resumeAnalysisService.markTokenCharged(analysisId,
                ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)) {
            return;
        }
        for (int attempt = 1; attempt <= TOKEN_CHARGE_MAX_ATTEMPTS; attempt++) {
            try {
                tokenFacadeService.useTokens(billingMemberId,
                        ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST);
                return;
            } catch (RuntimeException e) {
                if (attempt == TOKEN_CHARGE_MAX_ATTEMPTS) {
                    resumeAnalysisService.markTokenChargeFailed(analysisId);
                    log.error("이력서 분석 토큰 차감 실패, 결과는 제공 - analysisId: {}, memberId: {}",
                            analysisId, billingMemberId, e);
                    return;
                }
                sleepQuietly(TOKEN_CHARGE_BACKOFF);
            }
        }
    }

    private ResumeAnalysis readForUpdate(Long analysisId) {
        return resumeAnalysisRepository.findByIdForUpdate(analysisId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이력서 분석입니다. analysisId: " + analysisId));
    }

    /**
     * releaseLockSafely는 Lua CAS이므로 guest_lock_value를 정확히 알아야만 해제된다.
     * 무조건 삭제하는 releaseLock을 게스트 락에 쓰면 남의 락을 지울 수 있어 쓰지 않는다.
     */
    private void releaseGuestLockIfNeeded(ResumeAnalysis analysis) {
        if (!analysis.isGuest() || analysis.getGuestLockValue() == null) {
            return;
        }
        redisService.releaseLockSafely(
                ResumeAnalysisFacadeService.createGuestLockKey(analysis.getGuestIp()),
                analysis.getGuestLockValue());
        log.info("게스트 이력서 분석 락 해제 - guestIp: {}, lockValue: {}",
                analysis.getGuestIp(), analysis.getGuestLockValue());
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
