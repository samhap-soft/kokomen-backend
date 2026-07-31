package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisEvaluationFixture;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.service.dto.ExtractedContents;
import com.samhap.kokomen.resume.service.dto.GuestInfo;
import com.samhap.kokomen.resume.service.dto.MaterialRefs;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게스트 락 키·TTL·토큰 비용은 리터럴로 복제하지 않고 ResumeAnalysisFacadeService의 상수와
 * createGuestLockKey를 쓴다(같은 패키지이므로 import가 필요 없다).
 */
class ResumeAnalysisStateServiceTest extends BaseTest {

    private static final ResumeAnalysisJobInput JOB_INPUT =
            new ResumeAnalysisJobInput("백엔드 개발자", null, "신입");
    private static final ExtractedContents CONTENTS =
            new ExtractedContents("이력서 원문입니다.", null);
    private static final List<GeneratedQuestionDto> QUESTIONS = List.of(
            new GeneratedQuestionDto("질문 1", "이유 1"),
            new GeneratedQuestionDto("질문 2", "이유 2"),
            new GeneratedQuestionDto("질문 3", "이유 3"),
            new GeneratedQuestionDto("질문 4", "이유 4"),
            new GeneratedQuestionDto("질문 5", "이유 5"));

    @Autowired
    private ResumeAnalysisService resumeAnalysisService;

    @Autowired
    private ResumeAnalysisStateService resumeAnalysisStateService;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private RedisService redisService;

    @Test
    void PENDING_분석의_평가를_완료하면_EVALUATION_COMPLETED가_되고_question_started_at이_세팅된다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();

        // when
        boolean transited = resumeAnalysisStateService.completeEvaluation(analysisId,
                ResumeAnalysisEvaluationFixture.withoutJd());

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(transited).isTrue(),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(found.getProblemSolvingScore()).isEqualTo(90),
                () -> assertThat(found.getSoftSkillsImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(found.getJdFitScore()).isNull(),
                // StringListJsonConverter가 NULL 컬럼을 List.of()로 매핑하므로 DB 왕복 후에는 isEmpty()다.
                () -> assertThat(found.getJdFitReason()).isEmpty(),
                () -> assertThat(found.getJdFitImprovements()).isEmpty(),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getEvaluationCompletedAt()).isNotNull(),
                () -> assertThat(found.getQuestionStartedAt()).isNotNull()
        );
    }

    @Test
    void PENDING이_아닌_분석의_평가_완료는_상태_가드에_걸려_false를_반환한다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        boolean transited = resumeAnalysisStateService.completeEvaluation(analysisId,
                ResumeAnalysisEvaluationFixture.withJd());

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(transited).isFalse(),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getJdFitScore()).isNull()
        );
    }

    @Test
    void EVALUATION_COMPLETED_분석의_질문을_완료하면_COMPLETED가_되고_질문이_순서대로_저장된다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        boolean transited = resumeAnalysisStateService.completeQuestions(analysisId, QUESTIONS);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        List<GeneratedQuestion> saved =
                generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId);
        assertAll(
                () -> assertThat(transited).isTrue(),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getCompletedAt()).isNotNull(),
                () -> assertThat(saved).hasSize(5),
                () -> assertThat(saved).extracting(GeneratedQuestion::getQuestionOrder)
                        .containsExactly(0, 1, 2, 3, 4),
                () -> assertThat(saved.get(0).getContent()).isEqualTo("질문 1"),
                () -> assertThat(saved.get(4).getReason()).isEqualTo("이유 5")
        );
    }

    @Test
    void EVALUATION_COMPLETED가_아닌_분석의_질문_완료는_상태_가드에_걸려_질문이_저장되지_않는다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();

        // when
        boolean transited = resumeAnalysisStateService.completeQuestions(analysisId, QUESTIONS);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(transited).isFalse(),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .isEmpty()
        );
    }

    @Test
    void 질문_실패는_QUESTION_FAILED가_되고_평가_결과는_보존된다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(found.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.QUESTION_LLM),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getProblemSolvingScore()).isEqualTo(90)
        );
    }

    @Test
    void QUESTION_FAILED_분석은_재시도로_복원되고_재시도_횟수와_question_started_at이_갱신된다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);
        LocalDateTime before =
                resumeAnalysisRepository.findById(analysisId).orElseThrow().getQuestionStartedAt();

        // when
        resumeAnalysisStateService.restoreForQuestionRetry(analysisId);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(found.getFailureReason()).isNull(),
                () -> assertThat(found.getQuestionRetryCount()).isEqualTo(1),
                () -> assertThat(found.getQuestionStartedAt()).isAfter(before)
        );
    }

    @Test
    void QUESTION_FAILED가_아니면_재시도_복원은_예외가_발생한다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when & then
        assertThatThrownBy(() -> resumeAnalysisStateService.restoreForQuestionRetry(analysisId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("질문 재생성이 필요한 상태가 아닙니다.");
    }

    @Test
    void 재시도_상한에_도달한_분석은_복원되지_않는다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);
        resumeAnalysisStateService.restoreForQuestionRetry(analysisId);
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);
        resumeAnalysisStateService.restoreForQuestionRetry(analysisId);
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);

        // when & then
        assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow().getQuestionRetryCount())
                .isEqualTo(ResumeAnalysis.MAX_QUESTION_RETRY);
        assertThatThrownBy(() -> resumeAnalysisStateService.restoreForQuestionRetry(analysisId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("질문 재생성이 필요한 상태가 아닙니다.");
    }

    @Test
    void 게스트_분석의_평가_실패는_IP_락을_해제한다() {
        // given
        String guestIp = "11.22.33.74";
        String lockValue = UUID.randomUUID().toString();
        String lockKey = ResumeAnalysisFacadeService.createGuestLockKey(guestIp);
        redisService.acquireLockWithValue(lockKey, lockValue,
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
        Long analysisId = saveGuestAnalysis(guestIp, lockValue).getId();

        // when
        resumeAnalysisStateService.failEvaluation(analysisId, ResumeAnalysisFailureReason.EVALUATION_LLM);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(found.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.EVALUATION_LLM),
                () -> assertThat(redisTemplate.hasKey(lockKey)).isFalse()
        );
    }

    @Test
    void 게스트_분석의_질문_실패는_IP_락을_유지한다() {
        // given
        String guestIp = "11.22.33.75";
        String lockValue = UUID.randomUUID().toString();
        String lockKey = ResumeAnalysisFacadeService.createGuestLockKey(guestIp);
        redisService.acquireLockWithValue(lockKey, lockValue,
                ResumeAnalysisFacadeService.GUEST_RESUME_ANALYSIS_LOCK_TTL);
        Long analysisId = saveGuestAnalysis(guestIp, lockValue).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        resumeAnalysisStateService.failQuestions(analysisId, ResumeAnalysisFailureReason.QUESTION_LLM);

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(redisTemplate.hasKey(lockKey)).isTrue()
        );
    }

    @Test
    void PENDING이_아닌_분석의_평가_실패는_상태_가드에_걸려_전이되지_않는다() {
        // given
        Long analysisId = saveMemberAnalysis(false).getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        resumeAnalysisStateService.failEvaluation(analysisId, ResumeAnalysisFailureReason.STALE_SWEEP);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(found.getFailureReason()).isNull()
        );
    }

    @Test
    void 과금_대상_분석은_토큰_5개가_차감된다() {
        // given
        Member member = saveMemberWithTokens(20);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, true).getId();

        // when
        resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, member.getId());

        // then
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(freeToken.getTokenCount())
                        .isEqualTo(20 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(found.getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(found.isTokenChargeFailed()).isFalse()
        );
    }

    @Test
    void 무과금_분석은_토큰이_차감되지_않는다() {
        // given
        Member member = saveMemberWithTokens(20);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, false).getId();

        // when
        resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, null);

        // then
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        assertAll(
                () -> assertThat(freeToken.getTokenCount()).isEqualTo(20),
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow()
                        .getChargedTokenCount()).isZero()
        );
    }

    @Test
    void 같은_분석에_과금을_두_번_요청해도_이중_차감되지_않는다() {
        // given
        Member member = saveMemberWithTokens(20);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, true).getId();

        // when
        resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, member.getId());
        resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, member.getId());

        // then
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        assertAll(
                () -> assertThat(freeToken.getTokenCount())
                        .isEqualTo(20 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow()
                        .getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void 토큰_차감이_계속_실패하면_실패_플래그가_남고_charged_token_count는_0으로_돌아간다() {
        // given
        Member member = saveMemberWithTokens(0);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, true).getId();

        // when
        resumeAnalysisStateService.chargeTokensIfNeeded(analysisId, member.getId());

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.isTokenChargeFailed()).isTrue(),
                () -> assertThat(found.getChargedTokenCount()).isZero()
        );
    }

    private ResumeAnalysis saveMemberAnalysis(boolean billingRequired) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        return resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(), MaterialRefs.empty(),
                CONTENTS, JOB_INPUT, billingRequired);
    }

    private ResumeAnalysis saveGuestAnalysis(String guestIp, String guestLockValue) {
        return resumeAnalysisService.saveAnalysis(null,
                new GuestInfo(UUID.randomUUID().toString(), new ClientIp(guestIp), guestLockValue),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, false);
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
