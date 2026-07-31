package com.samhap.kokomen.global.fixture.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import java.util.List;
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

    @Test
    void guest_두_인자_버전을_지정하면_전달한_토큰과_IP가_그대로_반영된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .guest("guest-token-fixed", "1.2.3.4")
                .build();

        // then
        assertThat(analysis.getGuestToken()).isEqualTo("guest-token-fixed");
        assertThat(analysis.getGuestIp()).isEqualTo("1.2.3.4");
    }

    @Test
    void problemSolving을_지정하면_문제해결력_점수와_근거와_보완사항이_반영된다() {
        // given
        DimensionScore custom = new DimensionScore(15, List.of("커스텀근거"), List.of("커스텀보완"));

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .problemSolving(custom)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getProblemSolvingScore()).isEqualTo(15);
        assertThat(analysis.getProblemSolvingReason()).containsExactly("커스텀근거");
        assertThat(analysis.getProblemSolvingImprovements()).containsExactly("커스텀보완");
    }

    @Test
    void projectExperience를_지정하면_프로젝트경험_점수와_근거와_보완사항이_반영된다() {
        // given
        DimensionScore custom = new DimensionScore(25, List.of("커스텀근거"), List.of("커스텀보완"));

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .projectExperience(custom)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getProjectExperienceScore()).isEqualTo(25);
        assertThat(analysis.getProjectExperienceReason()).containsExactly("커스텀근거");
        assertThat(analysis.getProjectExperienceImprovements()).containsExactly("커스텀보완");
    }

    @Test
    void technicalSkills를_지정하면_기술력_점수와_근거와_보완사항이_반영된다() {
        // given
        DimensionScore custom = new DimensionScore(35, List.of("커스텀근거"), List.of("커스텀보완"));

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .technicalSkills(custom)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getTechnicalSkillsScore()).isEqualTo(35);
        assertThat(analysis.getTechnicalSkillsReason()).containsExactly("커스텀근거");
        assertThat(analysis.getTechnicalSkillsImprovements()).containsExactly("커스텀보완");
    }

    @Test
    void softSkills를_지정하면_소프트스킬_점수와_근거와_보완사항이_반영된다() {
        // given
        DimensionScore custom = new DimensionScore(45, List.of("커스텀근거"), List.of("커스텀보완"));

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .softSkills(custom)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getSoftSkillsScore()).isEqualTo(45);
        assertThat(analysis.getSoftSkillsReason()).containsExactly("커스텀근거");
        assertThat(analysis.getSoftSkillsImprovements()).containsExactly("커스텀보완");
    }

    @Test
    void jdFit을_지정하면_jobDescription이_있을_때_JD적합도_점수가_기본값_대신_반영된다() {
        // given
        DimensionScore custom = new DimensionScore(55, List.of("커스텀근거"), List.of("커스텀보완"));

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .jobDescription("Spring Boot 기반 백엔드 개발")
                .jdFit(custom)
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then — 기본 jd_fit은 70점이므로 55는 jdFit()이 실제로 적용됐을 때만 나온다
        assertThat(analysis.getJdFitScore()).isEqualTo(55);
        assertThat(analysis.getJdFitReason()).containsExactly("커스텀근거");
        assertThat(analysis.getJdFitImprovements()).containsExactly("커스텀보완");
    }

    @Test
    void resume를_지정하면_회원_행에_전달한_이력서가_그대로_연관된다() {
        // given
        Member member = MemberFixtureBuilder.builder().id(1L).build();
        MemberResume resume = MemberResumeFixtureBuilder.builder().member(member).build();

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .resume(resume)
                .build();

        // then
        assertThat(analysis.getMemberResume()).isSameAs(resume);
    }

    @Test
    void portfolio를_지정하면_회원_행에_전달한_포트폴리오가_그대로_연관된다() {
        // given
        Member member = MemberFixtureBuilder.builder().id(1L).build();
        MemberPortfolio portfolio = MemberPortfolioFixtureBuilder.builder().member(member).build();

        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .portfolio(portfolio)
                .build();

        // then
        assertThat(analysis.getMemberPortfolio()).isSameAs(portfolio);
    }

    @Test
    void jobPosition을_지정하면_기본_직무_대신_전달한_값이_저장된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .jobPosition("데이터 엔지니어")
                .build();

        // then
        assertThat(analysis.getJobPosition()).isEqualTo("데이터 엔지니어");
    }

    @Test
    void jobCareer를_지정하면_기본_경력_대신_전달한_값이_저장된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .jobCareer("3년차")
                .build();

        // then
        assertThat(analysis.getJobCareer()).isEqualTo("3년차");
    }

    @Test
    void totalFeedback을_지정하면_기본_총평_대신_전달한_값이_저장된다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .totalFeedback("커스텀 총평")
                .state(ResumeAnalysisState.COMPLETED)
                .build();

        // then
        assertThat(analysis.getTotalFeedback()).isEqualTo("커스텀 총평");
    }

    @Test
    void 기본_직무와_기본_경력은_지정하지_않으면_고정된_값으로_채워진다() {
        // when
        ResumeAnalysis analysis = ResumeAnalysisFixtureBuilder.builder()
                .build();

        // then — 기대값을 상수가 아니라 리터럴로 적는다. 상수를 되읽으면 그 상수를 어떻게 바꿔도
        // 통과해 고정하려던 값을 전혀 고정하지 못한다.
        assertThat(analysis.getJobPosition()).isEqualTo("백엔드 개발자");
        assertThat(analysis.getJobCareer()).isEqualTo("신입");
    }
}
