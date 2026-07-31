package com.samhap.kokomen.global.fixture.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import org.junit.jupiter.api.Test;

/**
 * Spring도 DB도 기동하지 않는 순수 엔티티 테스트다. StringListJsonConverter를 통과하지 않으므로
 * 미산출 jd_fit 컬렉션은 List.of()가 아니라 null이며, isNull() 단정이 정당하다.
 * (DB 왕복이 있는 테스트는 isEmpty()로 단정해야 한다 — 컨버터가 NULL을 List.of()로 매핑한다.)
 */
class ResumeAnalysisFixtureBuilderTest {

    @Test
    void 기본값은_JD_없음이고_jd_fit이_비어_있고_4지표_가중치로_총점이_계산된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then — 90/80/70/60 × JD_ABSENT(0.30/0.30/0.30/0.10) = 78
        assertThat(analysis.isJdProvided()).isFalse();
        assertThat(analysis.getJdFitScore()).isNull();
        assertThat(analysis.getJdFitReason()).isNull();
        assertThat(analysis.getJdFitImprovements()).isNull();
        assertThat(analysis.getTotalScore()).isEqualTo(78);
    }

    @Test
    void jobDescription을_지정하면_jd_fit이_자동으로_채워지고_JD포함_가중치가_적용된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then — 90/80/70/60/70 × JD_PROVIDED(0.25/0.25/0.25/0.10/0.15) = 76.5 → 77
        assertThat(analysis.isJdProvided()).isTrue();
        assertThat(analysis.getJdFitScore()).isEqualTo(70);
        assertThat(analysis.getTotalScore()).isEqualTo(77);
    }

    @Test
    void allDimensions로_전_차원_점수를_한_번에_지정한다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .allDimensions(80)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then — 80 × (0.30 + 0.30 + 0.30 + 0.10) = 80
        assertThat(analysis.getProblemSolvingScore()).isEqualTo(80);
        assertThat(analysis.getProjectExperienceScore()).isEqualTo(80);
        assertThat(analysis.getTechnicalSkillsScore()).isEqualTo(80);
        assertThat(analysis.getSoftSkillsScore()).isEqualTo(80);
        assertThat(analysis.getTotalScore()).isEqualTo(80);
    }

    @Test
    void totalScore를_지정하면_계산값을_덮어쓴다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .totalScore(55)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getTotalScore()).isEqualTo(55);
    }

    @Test
    void state로_QUESTION_FAILED를_지정하면_평가_결과는_남고_실패_원인이_기록된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .state(ResumeAnalysisState.QUESTION_FAILED)
                .build();

        // then
        assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.QUESTION_LLM);
        assertThat(analysis.getTotalScore()).isEqualTo(78);
    }

    @Test
    void state로_EVALUATION_FAILED를_지정하면_평가_결과가_비어_있다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .state(ResumeAnalysisState.EVALUATION_FAILED)
                .failureReason(ResumeAnalysisFailureReason.OUTPUT_TRUNCATED)
                .build();

        // then
        assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.OUTPUT_TRUNCATED);
        assertThat(analysis.getTotalScore()).isNull();
    }

    @Test
    void questionRetryCount는_엔티티_전이를_반복해_반영된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .state(ResumeAnalysisState.QUESTION_FAILED)
                .questionRetryCount(2)
                .build();

        // then
        assertThat(analysis.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED);
        assertThat(analysis.getQuestionRetryCount()).isEqualTo(2);
    }

    @Test
    void 기본값은_게스트_행이고_guest_token과_guest_lock_value가_채워진다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .guest()
                .build();

        // then
        assertThat(analysis.isGuest()).isTrue();
        assertThat(analysis.getGuestToken()).isNotBlank();
        assertThat(analysis.getGuestIp()).isEqualTo("11.22.33.99");
        assertThat(analysis.getGuestLockValue()).isNotBlank();
        assertThat(analysis.getGuestLockValue()).isNotEqualTo(analysis.getGuestToken());
    }

    @Test
    void member를_지정하면_회원_행이_되고_게스트_컬럼이_비어_있다() {
        // given
        Member member = MemberFixtureBuilder.builder().id(1L).build();

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .billingRequired(true)
                .build();

        // then
        assertThat(analysis.isGuest()).isFalse();
        assertThat(analysis.getGuestToken()).isNull();
        assertThat(analysis.isBillingRequired()).isTrue();
    }

    @Test
    void member와_guest를_동시에_지정하면_예외가_발생한다() {
        // given
        Member member = MemberFixtureBuilder.builder().id(1L).build();

        // when & then
        assertThatThrownBy(() -> ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .guest()
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("회원과 게스트를 동시에 지정할 수 없습니다.");
    }
}
