package com.samhap.kokomen.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAnalysisTest {

    @Test
    void 생성_직후_상태는_PENDING이다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();

        assertAll(
                () -> assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(analysis.getFailureReason()).isNull(),
                () -> assertThat(analysis.getChargedTokenCount()).isZero(),
                () -> assertThat(analysis.isTokenChargeFailed()).isFalse(),
                () -> assertThat(analysis.getQuestionRetryCount()).isZero(),
                () -> assertThat(analysis.getEvaluationCompletedAt()).isNull(),
                () -> assertThat(analysis.getQuestionStartedAt()).isNull(),
                () -> assertThat(analysis.getCompletedAt()).isNull(),
                () -> assertThat(analysis.isJdProvided()).isTrue()
        );
    }

    @Test
    void 평가_결과를_기록하면_EVALUATION_COMPLETED가_된다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();

        analysis.completeEvaluation(evaluationWithJdFit());

        assertAll(
                () -> assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(analysis.getProblemSolvingScore()).isEqualTo(90),
                () -> assertThat(analysis.getProblemSolvingReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(analysis.getProblemSolvingImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(analysis.getProjectExperienceScore()).isEqualTo(80),
                () -> assertThat(analysis.getProjectExperienceReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(analysis.getProjectExperienceImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(analysis.getTechnicalSkillsScore()).isEqualTo(70),
                () -> assertThat(analysis.getTechnicalSkillsReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(analysis.getTechnicalSkillsImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(analysis.getSoftSkillsScore()).isEqualTo(60),
                () -> assertThat(analysis.getSoftSkillsReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(analysis.getSoftSkillsImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(analysis.getJdFitScore()).isEqualTo(50),
                () -> assertThat(analysis.getJdFitReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(analysis.getJdFitImprovements()).containsExactly("보완1", "보완2"),
                () -> assertThat(analysis.getTotalScore()).isEqualTo(74),
                () -> assertThat(analysis.getTotalFeedback()).isEqualTo("총평입니다."),
                () -> assertThat(analysis.getEvaluationCompletedAt()).isNotNull(),
                () -> assertThat(analysis.getQuestionStartedAt()).isNotNull(),
                () -> assertThat(analysis.getCompletedAt()).isNull()
        );
    }

    @Test
    void 평가_기록_후_질문을_기록하면_COMPLETED가_된다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());

        analysis.completeQuestions();

        assertAll(
                () -> assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(analysis.getCompletedAt()).isNotNull(),
                () -> assertThat(analysis.getTotalScore()).isEqualTo(74)
        );
    }

    @Test
    void PENDING에서_질문을_먼저_기록하면_예외가_발생한다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();

        assertThatThrownBy(analysis::completeQuestions)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void EVALUATION_COMPLETED에서_평가를_다시_기록하면_예외가_발생한다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());

        assertThatThrownBy(() -> analysis.completeEvaluation(evaluationWithJdFit()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 평가_실패는_EVALUATION_FAILED이고_질문_실패는_QUESTION_FAILED다() {
        ResumeAnalysis evaluationFailed = memberAnalysisWithJd();
        evaluationFailed.failEvaluation(ResumeAnalysisFailureReason.EVALUATION_LLM);

        ResumeAnalysis questionFailed = memberAnalysisWithJd();
        questionFailed.completeEvaluation(evaluationWithJdFit());
        questionFailed.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);

        assertAll(
                () -> assertThat(evaluationFailed.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(evaluationFailed.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.EVALUATION_LLM),
                () -> assertThat(questionFailed.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(questionFailed.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.QUESTION_LLM)
        );
    }

    @Test
    void 질문_실패_상태에서도_평가_결과는_보존된다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());

        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);

        assertAll(
                () -> assertThat(analysis.getState().isEvaluationRevealed()).isTrue(),
                () -> assertThat(analysis.getProblemSolvingScore()).isEqualTo(90),
                () -> assertThat(analysis.getJdFitScore()).isEqualTo(50),
                () -> assertThat(analysis.getTotalScore()).isEqualTo(74),
                () -> assertThat(analysis.getTotalFeedback()).isEqualTo("총평입니다."),
                () -> assertThat(analysis.getEvaluationCompletedAt()).isNotNull()
        );
    }

    @Test
    void 질문_실패에서_재시도로_복원하면_EVALUATION_COMPLETED가_되고_재시도_횟수가_늘어난다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);

        analysis.restoreForQuestionRetry();

        assertAll(
                () -> assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_COMPLETED),
                () -> assertThat(analysis.getFailureReason()).isNull(),
                () -> assertThat(analysis.getQuestionRetryCount()).isEqualTo(1)
        );
    }

    @Test
    void 재시도_복원은_question_started_at을_갱신한다() throws InterruptedException {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());
        LocalDateTime firstQuestionStartedAt = analysis.getQuestionStartedAt();
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        Thread.sleep(2);

        analysis.restoreForQuestionRetry();

        assertThat(analysis.getQuestionStartedAt()).isAfter(firstQuestionStartedAt);
    }

    @Test
    void COMPLETED에서는_재시도로_복원할_수_없다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());
        analysis.completeQuestions();

        assertThatThrownBy(analysis::restoreForQuestionRetry)
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * DB 왕복이 없는 순수 엔티티 테스트이므로 StringListJsonConverter를 타지 않는다.
     * 따라서 jd_fit 리스트 2개는 isNull()로 단정한다(리포지토리 테스트는 isEmpty()로 단정한다).
     */
    @Test
    void JD가_없으면_JD적합성_3개_필드가_모두_null로_남는다() {
        ResumeAnalysis analysis = memberAnalysisWithoutJd();

        analysis.completeEvaluation(evaluationWithoutJdFit());

        assertAll(
                () -> assertThat(analysis.isJdProvided()).isFalse(),
                () -> assertThat(analysis.getJobDescription()).isNull(),
                () -> assertThat(analysis.getJdFitScore()).isNull(),
                () -> assertThat(analysis.getJdFitReason()).isNull(),
                () -> assertThat(analysis.getJdFitImprovements()).isNull(),
                () -> assertThat(analysis.getTotalScore()).isEqualTo(78)
        );
    }

    @Test
    void COMPLETED가_아니면_면접을_시작할_수_없다() {
        ResumeAnalysis analysis = memberAnalysisWithoutJd();
        analysis.completeEvaluation(evaluationWithoutJdFit());
        boolean beforeQuestions = analysis.getState().isQuestionReady();

        analysis.completeQuestions();

        assertAll(
                () -> assertThat(beforeQuestions).isFalse(),
                () -> assertThat(analysis.getState().isQuestionReady()).isTrue()
        );
    }

    @Test
    void 게스트_분석은_member가_null이고_guest_token과_guest_lock_value를_가진다() {
        ResumeAnalysis analysis = guestAnalysis("guest-token-1");

        assertAll(
                () -> assertThat(analysis.getMember()).isNull(),
                () -> assertThat(analysis.isGuest()).isTrue(),
                () -> assertThat(analysis.getGuestToken()).isEqualTo("guest-token-1"),
                () -> assertThat(analysis.getGuestIp()).isEqualTo("11.22.33.99"),
                () -> assertThat(analysis.getGuestLockValue()).isEqualTo("guest-lock-value-1"),
                () -> assertThat(analysis.isBillingRequired()).isFalse(),
                () -> assertThat(analysis.getMemberResume()).isNull(),
                () -> assertThat(analysis.getMemberPortfolio()).isNull()
        );
    }

    @Test
    void 회원_분석은_guest_token이_null이다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();

        assertAll(
                () -> assertThat(analysis.isGuest()).isFalse(),
                () -> assertThat(analysis.getGuestToken()).isNull(),
                () -> assertThat(analysis.getGuestIp()).isNull(),
                () -> assertThat(analysis.getGuestLockValue()).isNull(),
                () -> assertThat(analysis.isBillingRequired()).isTrue()
        );
    }

    @Test
    void isOwner는_게스트_행에서_예외없이_false를_반환한다() {
        ResumeAnalysis analysis = guestAnalysis("guest-token-1");

        assertAll(
                () -> assertThatCode(() -> analysis.isOwner(1L)).doesNotThrowAnyException(),
                () -> assertThat(analysis.isOwner(1L)).isFalse(),
                () -> assertThat(analysis.isOwner(null)).isFalse()
        );
    }

    @Test
    void 회원_분석은_소유자_ID가_일치할_때만_isOwner가_true다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();

        assertAll(
                () -> assertThat(analysis.isOwner(1L)).isTrue(),
                () -> assertThat(analysis.isOwner(2L)).isFalse(),
                () -> assertThat(analysis.isOwner(null)).isFalse()
        );
    }

    @Test
    void 다른_guest_token으로는_소유자로_인정되지_않는다() {
        ResumeAnalysis guest = guestAnalysis("guest-token-1");
        ResumeAnalysis member = memberAnalysisWithJd();

        assertAll(
                () -> assertThat(guest.isSameGuestToken("guest-token-1")).isTrue(),
                () -> assertThat(guest.isSameGuestToken("guest-token-2")).isFalse(),
                () -> assertThat(guest.isSameGuestToken(null)).isFalse(),
                () -> assertThat(member.isSameGuestToken("guest-token-1")).isFalse()
        );
    }

    @Test
    void 재시도_횟수가_상한이면_question_retryable은_false다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        boolean firstRetryable = analysis.isQuestionRetryable(true);

        analysis.restoreForQuestionRetry();
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);
        boolean secondRetryable = analysis.isQuestionRetryable(true);

        analysis.restoreForQuestionRetry();
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);

        assertAll(
                () -> assertThat(ResumeAnalysis.MAX_QUESTION_RETRY).isEqualTo(2),
                () -> assertThat(firstRetryable).isTrue(),
                () -> assertThat(secondRetryable).isTrue(),
                () -> assertThat(analysis.getQuestionRetryCount()).isEqualTo(2),
                () -> assertThat(analysis.isQuestionRetryable(true)).isFalse()
        );
    }

    @Test
    void 원문이_없으면_question_retryable은_false다() {
        ResumeAnalysis analysis = memberAnalysisWithJd();
        analysis.completeEvaluation(evaluationWithJdFit());
        analysis.failQuestions(ResumeAnalysisFailureReason.QUESTION_LLM);

        assertThat(analysis.isQuestionRetryable(false)).isFalse();
    }

    @Test
    void 질문_실패_상태가_아니면_question_retryable은_false다() {
        ResumeAnalysis pending = memberAnalysisWithJd();
        ResumeAnalysis evaluated = memberAnalysisWithJd();
        evaluated.completeEvaluation(evaluationWithJdFit());
        ResumeAnalysis completed = memberAnalysisWithJd();
        completed.completeEvaluation(evaluationWithJdFit());
        completed.completeQuestions();
        ResumeAnalysis evaluationFailed = memberAnalysisWithJd();
        evaluationFailed.failEvaluation(ResumeAnalysisFailureReason.EVALUATION_LLM);

        assertAll(
                () -> assertThat(pending.isQuestionRetryable(true)).isFalse(),
                () -> assertThat(evaluated.isQuestionRetryable(true)).isFalse(),
                () -> assertThat(completed.isQuestionRetryable(true)).isFalse(),
                () -> assertThat(evaluationFailed.isQuestionRetryable(true)).isFalse()
        );
    }

    private static ResumeAnalysis memberAnalysisWithJd() {
        return ResumeAnalysis.forMember(MemberFixtureBuilder.builder().id(1L).build(), null, null,
                new ResumeAnalysisJobInput("백엔드 개발자", "Spring Boot 기반 서비스 개발 경험", "3년"), true);
    }

    private static ResumeAnalysis memberAnalysisWithoutJd() {
        return ResumeAnalysis.forMember(MemberFixtureBuilder.builder().id(1L).build(), null, null,
                new ResumeAnalysisJobInput("백엔드 개발자", null, "3년"), true);
    }

    private static ResumeAnalysis guestAnalysis(String guestToken) {
        return ResumeAnalysis.forGuest(guestToken, new ClientIp("11.22.33.99"), "guest-lock-value-1",
                new ResumeAnalysisJobInput("백엔드 개발자", null, "3년"));
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
