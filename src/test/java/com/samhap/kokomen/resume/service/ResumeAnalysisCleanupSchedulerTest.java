package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.domain.ResumeAnalysisWeights;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ResumeAnalysisCleanupSchedulerTest extends BaseTest {

    @Autowired
    private ResumeAnalysisCleanupScheduler resumeAnalysisCleanupScheduler;
    @MockitoSpyBean
    private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired
    private ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RedisService redisService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 보존기간이_지난_미claim_게스트_분석과_질문이_삭제된다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.81");
        backdateCreatedAtDays(analysis.getId(), 31);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId())).isEmpty(),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysis.getId()))
                        .isEmpty()
        );
    }

    @Test
    void 원문_사이드_테이블도_함께_삭제된다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.82");
        backdateCreatedAtDays(analysis.getId(), 31);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(analysis.getId())).isEmpty();
    }

    @Test
    void 미claim_게스트_행은_종단_상태가_아니어도_원문까지_삭제된다() {
        // given — 종단 상태가 아니어서 만료 원문 정리의 대상이 아니다. 게스트 일괄 삭제만이 이 원문을 지울 수 있다
        ResumeAnalysis analysis = ResumeAnalysis.forGuest(UUID.randomUUID().toString(),
                new ClientIp("11.22.33.87"), UUID.randomUUID().toString(), jobInput());
        ResumeAnalysis saved = resumeAnalysisRepository.save(analysis);
        resumeAnalysisSourceTextRepository.save(new ResumeAnalysisSourceText(saved, "이력서 원문", null));
        backdateCreatedAtDays(saved.getId(), 31);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(saved.getId())).isEmpty(),
                () -> assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(saved.getId())).isEmpty()
        );
    }

    @Test
    void claim된_분석은_삭제되지_않는다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.83");
        backdateCreatedAtDays(analysis.getId(), 31);
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        jdbcTemplate.update("UPDATE resume_analysis SET member_id = ? WHERE id = ?",
                member.getId(), analysis.getId());

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId())).isPresent(),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysis.getId()))
                        .hasSize(5)
        );
    }

    @Test
    void 기준시간_이내의_게스트_분석은_삭제되지_않는다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.84");

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId())).isPresent(),
                () -> assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(analysis.getId())).isPresent()
        );
    }

    @Test
    void 면접이_참조하는_질문을_가진_분석은_대상에서_제외된다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.85");
        backdateCreatedAtDays(analysis.getId(), 31);
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        GeneratedQuestion question = generatedQuestionRepository
                .findByAnalysisIdOrderByQuestionOrder(analysis.getId()).get(0);
        interviewRepository.save(new Interview(member, question,
                Interview.MIN_ALLOWED_MAX_QUESTION_COUNT, InterviewMode.TEXT));

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then — FK 위반 없이 통과하고 해당 분석은 남는다
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId())).isPresent(),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysis.getId()))
                        .hasSize(5)
        );
    }

    @Test
    void 삭제_상한_건수를_초과하지_않는다() {
        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        verify(resumeAnalysisRepository, atLeastOnce())
                .findUnclaimedGuestAnalysisIds(any(LocalDateTime.class), eq(500));
    }

    @Test
    void 종단_상태의_만료된_원문은_별도로_삭제된다() {
        // given — 회원 소유라 행 자체는 보존 대상이다
        ResumeAnalysis saved = resumeAnalysisRepository.save(completedMemberAnalysis());
        resumeAnalysisSourceTextRepository.save(new ResumeAnalysisSourceText(saved, "이력서 원문", null));
        backdateCreatedAtDays(saved.getId(), 31);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(saved.getId())).isPresent(),
                () -> assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(saved.getId())).isEmpty()
        );
    }

    @Test
    void 종단_상태가_아닌_분석의_만료된_원문은_삭제되지_않는다() {
        // given — 질문 콜이 아직 진행 중일 수 있는 행의 원문은 재생성 재료로 남겨야 한다
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = ResumeAnalysis.forMember(member, null, null, jobInput(), false);
        analysis.completeEvaluation(jdAbsentEvaluation());
        ResumeAnalysis saved = resumeAnalysisRepository.save(analysis);
        resumeAnalysisSourceTextRepository.save(new ResumeAnalysisSourceText(saved, "이력서 원문", null));
        backdateCreatedAtDays(saved.getId(), 31);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(saved.getId())).isPresent();
    }

    @Test
    void 다른_인스턴스가_정리_락을_잡고_있으면_삭제하지_않는다() {
        // given
        ResumeAnalysis analysis = saveCompletedGuestAnalysis("11.22.33.86");
        backdateCreatedAtDays(analysis.getId(), 31);
        redisService.acquireLock(ResumeAnalysisCleanupScheduler.CLEANUP_LOCK_KEY,
                ResumeAnalysisCleanupScheduler.CLEANUP_LOCK_TTL);

        // when
        resumeAnalysisCleanupScheduler.deleteUnclaimedGuestAnalyses();

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysis.getId())).isPresent(),
                () -> assertThat(resumeAnalysisSourceTextRepository.findByAnalysisId(analysis.getId())).isPresent()
        );
    }

    @Test
    void 정리_스케줄_설정과_상수가_고정된다() throws NoSuchMethodException {
        // given
        Scheduled scheduled = ResumeAnalysisCleanupScheduler.class
                .getDeclaredMethod("deleteUnclaimedGuestAnalyses")
                .getAnnotation(Scheduled.class);

        // when & then
        assertAll(
                () -> assertThat(scheduled.cron()).isEqualTo("0 30 4 * * *"),
                () -> assertThat(scheduled.zone()).isEqualTo("Asia/Seoul"),
                () -> assertThat(ResumeAnalysisCleanupScheduler.CLEANUP_LOCK_KEY)
                        .isEqualTo("lock:resume-analysis:cleanup:scheduler"),
                () -> assertThat(ResumeAnalysisCleanupScheduler.CLEANUP_LOCK_TTL).isEqualTo(Duration.ofHours(1)),
                () -> assertThat(ResumeAnalysisCleanupScheduler.GUEST_RETENTION_DAYS).isEqualTo(30),
                () -> assertThat(ResumeAnalysisCleanupScheduler.SOURCE_TEXT_RETENTION_DAYS).isEqualTo(30),
                () -> assertThat(ResumeAnalysisCleanupScheduler.MAX_CLEANUP_COUNT).isEqualTo(500)
        );
    }

    private ResumeAnalysis saveCompletedGuestAnalysis(String guestIp) {
        ResumeAnalysis analysis = ResumeAnalysis.forGuest(
                UUID.randomUUID().toString(), new ClientIp(guestIp), UUID.randomUUID().toString(), jobInput());
        analysis.completeEvaluation(jdAbsentEvaluation());
        analysis.completeQuestions();
        ResumeAnalysis saved = resumeAnalysisRepository.save(analysis);
        resumeAnalysisSourceTextRepository.save(new ResumeAnalysisSourceText(saved, "이력서 원문", null));
        for (int questionOrder = 0; questionOrder < 5; questionOrder++) {
            generatedQuestionRepository.save(GeneratedQuestion.forAnalysis(
                    saved, "질문 " + questionOrder, "이유 " + questionOrder, questionOrder));
        }
        return saved;
    }

    private ResumeAnalysis completedMemberAnalysis() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis analysis = ResumeAnalysis.forMember(member, null, null, jobInput(), false);
        analysis.completeEvaluation(jdAbsentEvaluation());
        analysis.completeQuestions();
        return analysis;
    }

    private ResumeAnalysisJobInput jobInput() {
        return new ResumeAnalysisJobInput("백엔드 개발자", null, "신입");
    }

    private ResumeAnalysisEvaluation jdAbsentEvaluation() {
        ResumeAnalysisEvaluation evaluation = new ResumeAnalysisEvaluation(
                dimension(90), dimension(80), dimension(70), dimension(60), null, null, "종합 총평");
        return evaluation.withTotalScore(ResumeAnalysisWeights.JD_ABSENT.calculateTotalScore(evaluation));
    }

    private DimensionScore dimension(int score) {
        return new DimensionScore(score, List.of("근거1", "근거2"), List.of("보완1", "보완2"));
    }

    private void backdateCreatedAtDays(Long analysisId, int days) {
        jdbcTemplate.update("UPDATE resume_analysis SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(days), analysisId);
    }
}
