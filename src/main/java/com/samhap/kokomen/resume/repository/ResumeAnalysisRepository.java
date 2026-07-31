package com.samhap.kokomen.resume.repository;

import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.dto.ResumeAnalysisSummaryProjection;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    Optional<ResumeAnalysis> findByGuestToken(String guestToken);

    Page<ResumeAnalysisSummaryProjection> findSummariesByMemberId(Long memberId, Pageable pageable);

    boolean existsByMemberIdAndStateInAndCreatedAtAfter(
            Long memberId, Collection<ResumeAnalysisState> states, LocalDateTime since);

    boolean existsByMemberIdAndGuestTokenIsNotNull(Long memberId);

    @Query("""
            SELECT COUNT(a) > 0 FROM ResumeAnalysis a
             WHERE a.member.id = :memberId
               AND a.guestToken IS NULL
               AND (a.failureReason IS NULL
                    OR a.failureReason NOT IN (
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.CAPACITY,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.STALE_SWEEP,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.PERSISTENCE,
                        com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason.GUEST_LIMIT))
            """)
    boolean existsChargeableByMemberId(@Param("memberId") Long memberId);

    /**
     * MANDATORY로 고정한다. 호출자가 트랜잭션 없이 부르면 IllegalTransactionStateException으로 즉시 실패시켜야
     * 한다. REQUIRED였다면 호출자가 트랜잭션 경계를 빼먹었을 때 이 메서드가 자체적으로 트랜잭션을 열고 조회
     * 직후 커밋(락 해제)해버려서, 읽기-확인-쓰기 전체를 잠그지 못하는 동시성 버그를 조용히 감춘다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ResumeAnalysis a WHERE a.id = :id")
    Optional<ResumeAnalysis> findByIdForUpdate(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumeAnalysis a SET a.member = :member
             WHERE a.guestToken = :guestToken AND a.member IS NULL
            """)
    int claimByGuestToken(@Param("member") Member member, @Param("guestToken") String guestToken);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumeAnalysis a SET a.chargedTokenCount = :cost
             WHERE a.id = :id AND a.chargedTokenCount = 0
            """)
    int markTokenCharged(@Param("id") Long id, @Param("cost") int cost);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ResumeAnalysis a SET a.chargedTokenCount = 0, a.tokenChargeFailed = true WHERE a.id = :id")
    int markTokenChargeFailed(@Param("id") Long id);

    List<ResumeAnalysis> findByStateAndCreatedAtBefore(
            ResumeAnalysisState state, LocalDateTime threshold, Pageable pageable);

    List<ResumeAnalysis> findByStateAndQuestionStartedAtBefore(
            ResumeAnalysisState state, LocalDateTime threshold, Pageable pageable);

    @Query("""
            SELECT a.id FROM ResumeAnalysis a
             WHERE a.member IS NULL AND a.guestToken IS NOT NULL AND a.createdAt < :threshold
               AND NOT EXISTS (
                   SELECT 1 FROM GeneratedQuestion gq
                    WHERE gq.analysis = a
                      AND EXISTS (SELECT 1 FROM Interview i WHERE i.generatedQuestion = gq))
             ORDER BY a.id
             LIMIT :limit
            """)
    List<Long> findUnclaimedGuestAnalysisIds(@Param("threshold") LocalDateTime threshold,
                                             @Param("limit") int limit);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ResumeAnalysis a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * QUESTION_FAILED → EVALUATION_COMPLETED 조건부 전이. 재시도 상한과 상태를 WHERE에 함께 넣어
     * 동시 재시도 두 건 중 하나만 1행을 갱신하게 만든다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumeAnalysis a
               SET a.state = com.samhap.kokomen.resume.domain.ResumeAnalysisState.EVALUATION_COMPLETED,
                   a.failureReason = null,
                   a.questionRetryCount = a.questionRetryCount + 1,
                   a.questionStartedAt = :now
             WHERE a.id = :id
               AND a.state = com.samhap.kokomen.resume.domain.ResumeAnalysisState.QUESTION_FAILED
               AND a.questionRetryCount < :maxRetryCount
            """)
    int restoreForQuestionRetry(@Param("id") Long id, @Param("maxRetryCount") int maxRetryCount,
                                @Param("now") LocalDateTime now);

    /**
     * restoreForQuestionRetry를 되돌린다. 복원은 성공했지만 뒤이어 질문 hop을 실제로 시작하지 못한 경우에만
     * 쓴다 — 그대로 두면 실행된 적 없는 시도가 재생성 횟수를 하나 소모한다.
     * 조건부 전이이므로 그 사이 다른 주체가 행을 진전시켰다면 0행이 갱신되고 되돌리기는 일어나지 않는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ResumeAnalysis a
               SET a.state = com.samhap.kokomen.resume.domain.ResumeAnalysisState.QUESTION_FAILED,
                   a.failureReason = :reason,
                   a.questionRetryCount = a.questionRetryCount - 1
             WHERE a.id = :id
               AND a.state = com.samhap.kokomen.resume.domain.ResumeAnalysisState.EVALUATION_COMPLETED
               AND a.questionRetryCount > 0
            """)
    int revertQuestionRetry(@Param("id") Long id, @Param("reason") ResumeAnalysisFailureReason reason);
}
