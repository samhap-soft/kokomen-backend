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
 * <p>{@code @Transactional}이 없어도 벌크 삭제 세 개는 각자 자기 트랜잭션에서 돌아간다 — 세 리포지토리
 * 메서드 모두 {@code @Transactional}을 직접 달고 있다. 이 애노테이션이 하는 일은 그 셋을 <b>서로</b>
 * 원자적으로 묶는 것이다. 없으면 앞의 두 삭제가 독립적으로 커밋되므로, 부모 삭제가 실패했을 때 질문과 원문만
 * 사라지고 분석 행은 살아남아 질문 0개짜리 {@code COMPLETED} 분석이 조용히 남는다. 참여 트랜잭션에서 실패가
 * 나면 스프링이 공유 트랜잭션을 rollback-only로 표시하므로, 아래 {@code catch}가 예외를 삼켜도 커밋 시점에
 * 셋이 함께 되돌아간다.
 *
 * <p>{@code fk_generated_question_analysis}에는 {@code ON DELETE CASCADE}가 없으므로 자식인
 * {@code generated_question}을 반드시 먼저 지운다. {@code resume_analysis_source_text}는 CASCADE가 걸려
 * 있지만 명시적으로 지운다 — 삭제 순서가 코드에서 읽히고, 그 CASCADE가 나중에 바뀌어도 이 배치가 조용히
 * 원문을 남기지 않는다. CASCADE와 중복되어도 이미 지워진 행에 대한 DELETE는 0행이라 무해하다.
 *
 * <p>전역 락은 정확성 장치가 아니라 중복 실행을 줄이는 장치다. 두 인스턴스가 같은 회차를 돌아도 결과는 같다 —
 * 뒤에 온 쪽의 조회는 같은 id 집합을 보고, 그쪽의 자식 삭제는 앞선 쪽이 쥔 행 락에서 잠깐 대기한 뒤 0행에
 * 적용된다. 두 인스턴스가 같은 인덱스 위에서 같은 순서(자식 → 부모)로 단일 문장씩 지우므로 락 순서 역전이
 * 없고, 한쪽이 롤백되더라도 그 인스턴스가 멱등한 정리를 다시 도는 것으로 끝난다. 락을 잡지 못한 인스턴스는
 * 대기하지 않고 이번 회차를 건너뛴다 — 하루 한 번 도는 배치라 다음 회차로 미뤄도 잃는 것이 없다.
 *
 * <p>회차가 끝나면 {@code finally}에서 해제한다. TTL(1시간)도 다음 실행(24시간 뒤)보다 훨씬 짧아 어차피 만료
 * 되므로, 해제의 목적은 급사한 회차가 한 시간 동안 키를 쥐고 있지 않게 하는 것뿐이다. 해제가 커밋보다 앞서
 * 일어나는데, 위에서 본 이유로 이 배치에서는 그 창이 무해하다. 남의 락을 지우지 않도록 해제는 Lua CAS인
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
