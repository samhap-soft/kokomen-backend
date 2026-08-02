package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원에게 귀속되지 않은 게스트 분석 행과, 보존기간이 지난 추출 원문을 정리한다.
 * 고착 행을 종단 상태로 옮기는 일은 {@code ResumeAnalysisRecoveryScheduler}의 책임이고 이 클래스는 삭제만 한다.
 *
 * <p>{@code @Transactional}이 필요한 이유는 벌크 삭제 세 개가 FK 순서대로 한 트랜잭션에서 실행되어야 하고
 * 리포지토리 메서드 자체에는 트랜잭션 경계가 없기 때문이다. {@code fk_generated_question_analysis}에는
 * {@code ON DELETE CASCADE}가 없으므로 자식인 {@code generated_question}을 반드시 먼저 지운다.
 * {@code resume_analysis_source_text}는 CASCADE가 걸려 있지만 명시적으로 지운다 — 삭제 순서가 코드에서
 * 읽히고, 그 CASCADE가 나중에 바뀌어도 이 배치가 조용히 원문을 남기지 않는다. CASCADE와 중복되어도 이미
 * 지워진 행에 대한 DELETE는 0행이라 무해하다.
 *
 * <p>전역 락은 여기서는 정확성 장치다. 두 인스턴스가 같은 id 집합을 동시에 지우면 한쪽이 자식을 지우는 사이
 * 다른 쪽이 부모를 지워 FK 위반으로 배치 전체가 롤백될 수 있다. 락을 잡지 못한 인스턴스는 대기하지 않고
 * 이번 회차를 건너뛴다 — 하루 한 번 도는 배치라 다음 회차로 미뤄도 잃는 것이 없다.
 * 회차가 끝나면 곧바로 해제하고 TTL은 실행 중 급사에 대비한 상한으로만 둔다. 해제가 커밋보다 앞서므로,
 * 커밋 시점에 예외가 나도 락이 남아 다음 회차를 막지 않는다. 남의 락을 지우지 않도록 해제는 Lua CAS인
 * {@code releaseLockSafely}로 한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ResumeAnalysisCleanupScheduler {

    public static final String CLEANUP_LOCK_KEY = "lock:resume-analysis:cleanup:scheduler";
    public static final Duration CLEANUP_LOCK_TTL = Duration.ofHours(1);
    public static final int GUEST_RETENTION_DAYS = 30;
    public static final int SOURCE_TEXT_RETENTION_DAYS = 30;
    public static final int MAX_CLEANUP_COUNT = 500;

    // 종단 상태 목록을 손으로 적지 않고 isTerminal에서 유도한다. 상태가 추가될 때 한쪽만 갱신되는 것을 막는다.
    private static final List<ResumeAnalysisState> TERMINAL_STATES = Arrays.stream(ResumeAnalysisState.values())
            .filter(ResumeAnalysisState::isTerminal)
            .toList();

    private final RedisService redisService;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteUnclaimedGuestAnalyses() {
        String lockValue = UUID.randomUUID().toString();
        if (!redisService.acquireLockWithValue(CLEANUP_LOCK_KEY, lockValue, CLEANUP_LOCK_TTL)) {
            log.debug("미claim 게스트 분석 정리 스킵 - 다른 인스턴스가 실행 중");
            return;
        }

        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(GUEST_RETENTION_DAYS);
            List<Long> analysisIds = resumeAnalysisRepository.findUnclaimedGuestAnalysisIds(
                    threshold, MAX_CLEANUP_COUNT);
            if (!analysisIds.isEmpty()) {
                generatedQuestionRepository.deleteByAnalysisIdIn(analysisIds);
                resumeAnalysisSourceTextRepository.deleteByAnalysisIdIn(analysisIds);
                resumeAnalysisRepository.deleteByIds(analysisIds);
            }
            purgeExpiredSourceTexts();
            log.info("미claim 게스트 분석 정리 - analyses: {}", analysisIds.size());
            if (analysisIds.size() >= MAX_CLEANUP_COUNT) {
                log.warn("게스트 분석 정리 상한 도달 - 남은 백로그가 있다");
            }
        } catch (Exception e) {
            log.error("게스트 분석 정리 실패", e);
        } finally {
            redisService.releaseLockSafely(CLEANUP_LOCK_KEY, lockValue);
        }
    }

    /**
     * 종단 상태에 도달한 행의 원문만 만료시킨다. LONGTEXT가 무한히 쌓이는 것을 막는 것이 목적이고, 아직
     * 종단되지 않은 행은 질문 콜이 진행 중일 수 있어 원문이 재생성 재료로 남아 있어야 한다.
     * 위의 게스트 일괄 삭제와 대상이 겹칠 수 있지만, 그쪽이 먼저 지운 행은 여기서 조회되지 않는다.
     */
    private void purgeExpiredSourceTexts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(SOURCE_TEXT_RETENTION_DAYS);
        List<Long> analysisIds = resumeAnalysisSourceTextRepository.findExpiredAnalysisIds(
                TERMINAL_STATES, threshold, MAX_CLEANUP_COUNT);
        if (analysisIds.isEmpty()) {
            return;
        }
        int deletedCount = resumeAnalysisSourceTextRepository.deleteByAnalysisIdIn(analysisIds);
        log.info("만료된 이력서 분석 원문 정리 - sourceTexts: {}", deletedCount);
    }
}
