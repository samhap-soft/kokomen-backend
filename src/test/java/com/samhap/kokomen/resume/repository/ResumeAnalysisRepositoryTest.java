package com.samhap.kokomen.resume.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.dto.QuestionCountProjection;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.dto.ResumeAnalysisSummaryProjection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class ResumeAnalysisRepositoryTest extends BaseTest {

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired
    private ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;
    @Autowired
    private MemberResumeRepository memberResumeRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 게스트_분석을_저장하고_guest_token으로_조회한다() {
        ResumeAnalysis saved = resumeAnalysisRepository.save(guestAnalysis("guest-token-1"));

        Optional<ResumeAnalysis> found = resumeAnalysisRepository.findByGuestToken("guest-token-1");

        assertAll(
                () -> assertThat(found).isPresent(),
                () -> assertThat(found.get().getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.get().isGuest()).isTrue(),
                () -> assertThat(found.get().getGuestIp()).isEqualTo("11.22.33.99"),
                () -> assertThat(found.get().getGuestLockValue()).isEqualTo("guest-lock-value-1"),
                () -> assertThat(found.get().getCreatedAt()).isNotNull(),
                () -> assertThat(resumeAnalysisRepository.findByGuestToken("guest-token-2")).isEmpty()
        );
    }

    @Test
    void 회원_분석은_이력서_FK와_15지표_JSON_컬럼이_왕복한다() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume memberResume = memberResumeRepository.save(
                new MemberResume(member, "이력서", "https://s3.example.com/resume.pdf", "이력서 원문"));
        ResumeAnalysis analysis = ResumeAnalysis.forMember(member, memberResume, null,
                new ResumeAnalysisJobInput("백엔드 개발자", "Spring Boot 경험", "3년"), true);
        analysis.completeEvaluation(evaluationWithJdFit());
        Long analysisId = resumeAnalysisRepository.save(analysis).getId();

        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();

        assertAll(
                () -> assertThat(found.getMember().getId()).isEqualTo(member.getId()),
                () -> assertThat(found.getMemberResume().getId()).isEqualTo(memberResume.getId()),
                () -> assertThat(found.getMemberPortfolio()).isNull(),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(found.isJdProvided()).isTrue(),
                () -> assertThat(found.getJobDescription()).isEqualTo("Spring Boot 경험"),
                () -> assertThat(found.getProblemSolvingReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(found.getProblemSolvingImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(found.getProjectExperienceReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(found.getTechnicalSkillsReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(found.getSoftSkillsReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(found.getJdFitReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(found.getJdFitImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(found.getTotalScore()).isEqualTo(74),
                () -> assertThat(found.getEvaluationCompletedAt()).isNotNull(),
                () -> assertThat(found.getQuestionStartedAt()).isNotNull()
        );
    }

    /**
     * jd_fit 컬럼 3개는 DB에 NULL로 저장된다. 다만 StringListJsonConverter.convertToEntityAttribute가
     * NULL/blank를 List.of()로 매핑하므로(레포 기존 구현, 변경 금지) DB 왕복 후 리스트 게터는 빈 리스트를 반환한다.
     * jd_fit_score는 Integer로 컨버터를 타지 않아 null이 유지된다.
     * "미산출" 판정은 DTO 경계에서 score == null이 담당하므로 계약에는 영향이 없다.
     */
    @Test
    void JD가_없으면_jd_fit_컬럼_3개가_null로_저장되고_리스트는_빈_값으로_읽힌다() {
        ResumeAnalysis analysis = guestAnalysis("guest-token-1");
        analysis.completeEvaluation(evaluationWithoutJdFit());
        Long analysisId = resumeAnalysisRepository.save(analysis).getId();

        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();

        assertAll(
                () -> assertThat(found.isJdProvided()).isFalse(),
                () -> assertThat(found.getJobDescription()).isNull(),
                () -> assertThat(found.getJdFitScore()).isNull(),
                () -> assertThat(found.getJdFitReason()).isEmpty(),
                () -> assertThat(found.getJdFitImprovements()).isEmpty(),
                () -> assertThat(found.getTotalScore()).isEqualTo(78)
        );
    }

    @Test
    void 회원_요약_목록을_페이지로_조회한다() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis first = memberAnalysisWithoutJd(member);
        first.completeEvaluation(evaluationWithoutJdFit());
        resumeAnalysisRepository.save(first);
        resumeAnalysisRepository.save(memberAnalysisWithoutJd(member));

        Page<ResumeAnalysisSummaryProjection> page =
                resumeAnalysisRepository.findSummariesByMemberId(member.getId(), PageRequest.of(0, 10));

        assertAll(
                () -> assertThat(page.getTotalElements()).isEqualTo(2),
                () -> assertThat(page.getContent())
                        .extracting(ResumeAnalysisSummaryProjection::getState,
                                ResumeAnalysisSummaryProjection::getJobPosition,
                                ResumeAnalysisSummaryProjection::getJobCareer,
                                ResumeAnalysisSummaryProjection::isJdProvided,
                                ResumeAnalysisSummaryProjection::getTotalScore)
                        .containsExactlyInAnyOrder(
                                tuple(ResumeAnalysisState.EVALUATION_COMPLETED, "백엔드 개발자", "3년", false, 78),
                                tuple(ResumeAnalysisState.PENDING, "백엔드 개발자", "3년", false, null)),
                () -> assertThat(page.getContent().get(0).getId()).isNotNull(),
                () -> assertThat(page.getContent().get(0).getCreatedAt()).isNotNull()
        );
    }

    @Test
    void 진행_중_분석_존재_검사는_상태와_생성시각을_함께_본다() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(memberAnalysisWithoutJd(member));
        List<ResumeAnalysisState> inProgress =
                List.of(ResumeAnalysisState.PENDING, ResumeAnalysisState.EVALUATION_COMPLETED);

        assertAll(
                () -> assertThat(resumeAnalysisRepository.existsByMemberIdAndStateInAndCreatedAtAfter(
                        member.getId(), inProgress, LocalDateTime.now().minusMinutes(10))).isTrue(),
                () -> assertThat(resumeAnalysisRepository.existsByMemberIdAndStateInAndCreatedAtAfter(
                        member.getId(), inProgress, LocalDateTime.now().plusMinutes(10))).isFalse(),
                () -> assertThat(resumeAnalysisRepository.existsByMemberIdAndStateInAndCreatedAtAfter(
                        member.getId(), List.of(ResumeAnalysisState.COMPLETED),
                        LocalDateTime.now().minusMinutes(10))).isFalse()
        );
    }

    @Test
    void claim은_member_id가_비어있는_행만_갱신한다() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long analysisId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();

        int firstClaimed = resumeAnalysisRepository.claimByGuestToken(member, "guest-token-1");
        int secondClaimed = resumeAnalysisRepository.claimByGuestToken(member, "guest-token-1");
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();

        assertAll(
                () -> assertThat(firstClaimed).isEqualTo(1),
                () -> assertThat(secondClaimed).isZero(),
                () -> assertThat(found.getMember().getId()).isEqualTo(member.getId()),
                () -> assertThat(found.getGuestToken()).isEqualTo("guest-token-1"),
                () -> assertThat(found.isGuest()).isFalse(),
                () -> assertThat(resumeAnalysisRepository
                        .existsByMemberIdAndGuestTokenIsNotNull(member.getId())).isTrue()
        );
    }

    @Test
    void 토큰_과금_선점은_한_번만_성공하고_실패_기록은_카운트를_되돌린다() {
        Long analysisId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();

        int firstCharged = resumeAnalysisRepository.markTokenCharged(analysisId, 5);
        int secondCharged = resumeAnalysisRepository.markTokenCharged(analysisId, 5);
        ResumeAnalysis charged = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        resumeAnalysisRepository.markTokenChargeFailed(analysisId);
        ResumeAnalysis failed = resumeAnalysisRepository.findById(analysisId).orElseThrow();

        assertAll(
                () -> assertThat(firstCharged).isEqualTo(1),
                () -> assertThat(secondCharged).isZero(),
                () -> assertThat(charged.getChargedTokenCount()).isEqualTo(5),
                () -> assertThat(charged.isTokenChargeFailed()).isFalse(),
                () -> assertThat(failed.getChargedTokenCount()).isZero(),
                () -> assertThat(failed.isTokenChargeFailed()).isTrue()
        );
    }

    @Test
    void 첫_사용_판정은_서버_귀책_실패와_claim된_게스트_행을_제외한다() {
        Member normalMember = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(memberAnalysisWithoutJd(normalMember));

        Member capacityMember = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis capacityFailed = memberAnalysisWithoutJd(capacityMember);
        capacityFailed.failEvaluation(ResumeAnalysisFailureReason.CAPACITY);
        resumeAnalysisRepository.save(capacityFailed);

        Member claimedMember = memberRepository.save(MemberFixtureBuilder.builder().build());
        resumeAnalysisRepository.save(guestAnalysis("guest-token-1"));
        resumeAnalysisRepository.claimByGuestToken(claimedMember, "guest-token-1");

        assertAll(
                () -> assertThat(resumeAnalysisRepository
                        .existsChargeableByMemberId(normalMember.getId())).isTrue(),
                () -> assertThat(resumeAnalysisRepository
                        .existsChargeableByMemberId(capacityMember.getId())).isFalse(),
                () -> assertThat(resumeAnalysisRepository
                        .existsChargeableByMemberId(claimedMember.getId())).isFalse()
        );
    }

    @Test
    void 잔류_행은_상태와_시각으로_조회되고_락_조회는_같은_행을_반환한다() {
        Long pendingId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();
        ResumeAnalysis evaluated = guestAnalysis("guest-token-2");
        evaluated.completeEvaluation(evaluationWithoutJdFit());
        Long evaluatedId = resumeAnalysisRepository.save(evaluated).getId();
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(1);

        List<ResumeAnalysis> stalePending = resumeAnalysisRepository.findByStateAndCreatedAtBefore(
                ResumeAnalysisState.PENDING, threshold, PageRequest.of(0, 200));
        List<ResumeAnalysis> staleQuestion = resumeAnalysisRepository.findByStateAndQuestionStartedAtBefore(
                ResumeAnalysisState.EVALUATION_COMPLETED, threshold, PageRequest.of(0, 200));
        // findByIdForUpdate는 MANDATORY라 앰비언트 트랜잭션 없이는 호출할 수 없다(Task 8 실사용과 동일한 경계).
        Long lockedId = transactionTemplate.execute(status ->
                resumeAnalysisRepository.findByIdForUpdate(pendingId).orElseThrow().getId());

        assertAll(
                () -> assertThat(stalePending).extracting(ResumeAnalysis::getId).containsExactly(pendingId),
                () -> assertThat(staleQuestion).extracting(ResumeAnalysis::getId).containsExactly(evaluatedId),
                () -> assertThat(resumeAnalysisRepository.findByStateAndCreatedAtBefore(
                        ResumeAnalysisState.PENDING, LocalDateTime.now().minusMinutes(1),
                        PageRequest.of(0, 200))).isEmpty(),
                () -> assertThat(lockedId).isEqualTo(pendingId)
        );
    }

    @Test
    void findByIdForUpdate는_트랜잭션_안에서_읽기와_쓰기를_원자적으로_수행한다() {
        Long analysisId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();

        transactionTemplate.executeWithoutResult(status -> {
            ResumeAnalysis locked = resumeAnalysisRepository.findByIdForUpdate(analysisId).orElseThrow();
            locked.failEvaluation(ResumeAnalysisFailureReason.EVALUATION_LLM);
        });

        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED);
    }

    @Test
    void findByIdForUpdate는_트랜잭션_없이_호출하면_예외가_발생한다() {
        Long analysisId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();

        assertThatThrownBy(() -> resumeAnalysisRepository.findByIdForUpdate(analysisId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /**
     * firstAcquired 래치는 "첫 번째 트랜잭션이 findByIdForUpdate로 행 락을 이미 획득했다"만 보장한다.
     * 두 번째 트랜잭션의 같은 호출은 그 행 락 때문에 DB 단에서 대기하다가 첫 번째가 커밋(=락 해제)된
     * 뒤에야 반환되므로, 실행 순서는 sleep 시간과 무관하게 결정적이다(타이밍 경합에 의존하지 않는다).
     */
    @Test
    void 동시에_findByIdForUpdate를_호출하면_두_번째_호출은_첫_번째_커밋_후에_진행된다() throws InterruptedException {
        Long analysisId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();
        List<String> executionLog = new CopyOnWriteArrayList<>();
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        executorService.execute(() -> transactionTemplate.executeWithoutResult(status -> {
            resumeAnalysisRepository.findByIdForUpdate(analysisId).orElseThrow();
            executionLog.add("first-enter");
            firstAcquired.countDown();
            sleepQuietly(200);
            executionLog.add("first-exit");
        }));
        assertThat(firstAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        executorService.execute(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                resumeAnalysisRepository.findByIdForUpdate(analysisId).orElseThrow();
                executionLog.add("second-enter");
            });
            secondDone.countDown();
        });
        assertThat(secondDone.await(5, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();

        assertThat(executionLog).containsExactly("first-enter", "first-exit", "second-enter");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 면접에_사용되지_않은_미claim_게스트_분석_ID만_조회된다() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long unclaimedId = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();

        resumeAnalysisRepository.save(guestAnalysis("guest-token-2"));
        resumeAnalysisRepository.claimByGuestToken(member, "guest-token-2");

        ResumeAnalysis interviewed = resumeAnalysisRepository.save(guestAnalysis("guest-token-3"));
        GeneratedQuestion question = generatedQuestionRepository.save(
                GeneratedQuestion.forAnalysis(interviewed, "질문 내용", "질문 이유", 1));
        interviewRepository.save(new Interview(member, question, 3, InterviewMode.TEXT));

        List<Long> ids = resumeAnalysisRepository.findUnclaimedGuestAnalysisIds(
                LocalDateTime.now().plusMinutes(1), 100);

        assertThat(ids).containsExactly(unclaimedId);
    }

    @Test
    void 원문_사이드_테이블은_analysis_id로_조회되고_일괄_삭제된다() {
        ResumeAnalysis analysis = resumeAnalysisRepository.save(guestAnalysis("guest-token-1"));
        resumeAnalysisSourceTextRepository.save(
                new ResumeAnalysisSourceText(analysis, "이력서 원문", "포트폴리오 원문"));

        Optional<ResumeAnalysisSourceText> found =
                resumeAnalysisSourceTextRepository.findByAnalysisId(analysis.getId());
        boolean existsBeforeDelete = resumeAnalysisSourceTextRepository.existsByAnalysisId(analysis.getId());
        int deleted = resumeAnalysisSourceTextRepository.deleteByAnalysisIdIn(List.of(analysis.getId()));

        assertAll(
                () -> assertThat(found).isPresent(),
                () -> assertThat(found.get().getResumeContent()).isEqualTo("이력서 원문"),
                () -> assertThat(found.get().getPortfolioContent()).isEqualTo("포트폴리오 원문"),
                () -> assertThat(found.get().hasPortfolioContent()).isTrue(),
                () -> assertThat(existsBeforeDelete).isTrue(),
                () -> assertThat(deleted).isEqualTo(1),
                () -> assertThat(resumeAnalysisSourceTextRepository
                        .existsByAnalysisId(analysis.getId())).isFalse()
        );
    }

    @Test
    void 분석용_질문은_analysis_id로_정렬_조회되고_귀속_검증과_집계가_동작한다() {
        ResumeAnalysis analysis = resumeAnalysisRepository.save(guestAnalysis("guest-token-1"));
        ResumeAnalysis other = resumeAnalysisRepository.save(guestAnalysis("guest-token-2"));
        generatedQuestionRepository.save(GeneratedQuestion.forAnalysis(analysis, "두번째 질문", "이유2", 2));
        GeneratedQuestion first = generatedQuestionRepository.save(
                GeneratedQuestion.forAnalysis(analysis, "첫번째 질문", "이유1", 1));

        List<GeneratedQuestion> questions =
                generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysis.getId());
        List<QuestionCountProjection> counts =
                generatedQuestionRepository.countByAnalysisIdIn(List.of(analysis.getId(), other.getId()));

        assertAll(
                () -> assertThat(questions).extracting(GeneratedQuestion::getContent)
                        .containsExactly("첫번째 질문", "두번째 질문"),
                () -> assertThat(questions).allSatisfy(q -> assertThat(q.getGeneration()).isNull()),
                () -> assertThat(generatedQuestionRepository
                        .findByIdAndAnalysisId(first.getId(), analysis.getId())).isPresent(),
                () -> assertThat(generatedQuestionRepository
                        .findByIdAndAnalysisId(first.getId(), other.getId())).isEmpty(),
                () -> assertThat(counts)
                        .extracting(QuestionCountProjection::getAnalysisId,
                                QuestionCountProjection::getQuestionCount)
                        .containsExactly(tuple(analysis.getId(), 2L)),
                () -> assertThat(generatedQuestionRepository
                        .deleteByAnalysisIdIn(List.of(analysis.getId()))).isEqualTo(2)
        );
    }

    @Test
    void 분석_행을_ID로_일괄_삭제한다() {
        Long first = resumeAnalysisRepository.save(guestAnalysis("guest-token-1")).getId();
        Long second = resumeAnalysisRepository.save(guestAnalysis("guest-token-2")).getId();

        int deleted = resumeAnalysisRepository.deleteByIds(List.of(first, second));

        assertAll(
                () -> assertThat(deleted).isEqualTo(2),
                () -> assertThat(resumeAnalysisRepository.count()).isZero()
        );
    }

    private static ResumeAnalysis guestAnalysis(String guestToken) {
        return ResumeAnalysis.forGuest(guestToken, new ClientIp("11.22.33.99"), "guest-lock-value-1",
                new ResumeAnalysisJobInput("백엔드 개발자", null, "3년"));
    }

    private static ResumeAnalysis memberAnalysisWithoutJd(Member member) {
        return ResumeAnalysis.forMember(member, null, null,
                new ResumeAnalysisJobInput("백엔드 개발자", null, "3년"), true);
    }

    private static ResumeAnalysisEvaluation evaluationWithJdFit() {
        return new ResumeAnalysisEvaluation(dimension(90), dimension(80), dimension(70), dimension(60),
                dimension(50), 74, "총평입니다.");
    }

    private static ResumeAnalysisEvaluation evaluationWithoutJdFit() {
        return new ResumeAnalysisEvaluation(dimension(90), dimension(80), dimension(70), dimension(60),
                null, 78, "총평입니다.");
    }

    private static DimensionScore dimension(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }
}
