package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisEvaluationFixture;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import com.samhap.kokomen.resume.service.dto.ExtractedContents;
import com.samhap.kokomen.resume.service.dto.GuestInfo;
import com.samhap.kokomen.resume.service.dto.MaterialRefs;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ResumeAnalysisServiceTest extends BaseTest {

    private static final ResumeAnalysisJobInput JOB_INPUT_WITHOUT_JD =
            new ResumeAnalysisJobInput("백엔드 개발자", null, "신입");
    private static final ResumeAnalysisJobInput JOB_INPUT_WITH_JD =
            new ResumeAnalysisJobInput("백엔드 개발자", "Java, Spring Boot 경험자를 찾습니다.", "경력 3년");
    private static final ExtractedContents CONTENTS =
            new ExtractedContents("이력서 원문입니다.", "포트폴리오 원문입니다.");

    @Autowired
    private ResumeAnalysisService resumeAnalysisService;

    @Autowired
    private ResumeAnalysisStateService resumeAnalysisStateService;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 회원_분석을_저장하면_PENDING_행과_원문이_함께_저장된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());

        // when
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITH_JD, true);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(saved.getId()).orElseThrow();
        ResumeAnalysisSourceText sourceText =
                resumeAnalysisSourceTextRepository.findByAnalysisId(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.PENDING),
                () -> assertThat(found.isGuest()).isFalse(),
                () -> assertThat(found.getGuestToken()).isNull(),
                () -> assertThat(found.isJdProvided()).isTrue(),
                () -> assertThat(found.isBillingRequired()).isTrue(),
                () -> assertThat(found.getChargedTokenCount()).isZero(),
                () -> assertThat(found.getQuestionRetryCount()).isZero(),
                () -> assertThat(found.getJobPosition()).isEqualTo("백엔드 개발자"),
                () -> assertThat(found.getJobCareer()).isEqualTo("경력 3년"),
                () -> assertThat(sourceText.getResumeContent()).isEqualTo("이력서 원문입니다."),
                () -> assertThat(sourceText.getPortfolioContent()).isEqualTo("포트폴리오 원문입니다.")
        );
    }

    @Test
    void 게스트_분석을_저장하면_member가_null이고_guest_token과_guest_lock_value가_저장된다() {
        // given
        String guestToken = UUID.randomUUID().toString();
        String guestLockValue = UUID.randomUUID().toString();

        // when
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(null,
                new GuestInfo(guestToken, new ClientIp("11.22.33.71"), guestLockValue),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITHOUT_JD, false);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(found.isGuest()).isTrue(),
                () -> assertThat(found.getGuestToken()).isEqualTo(guestToken),
                () -> assertThat(found.getGuestIp()).isEqualTo("11.22.33.71"),
                () -> assertThat(found.getGuestLockValue()).isEqualTo(guestLockValue),
                () -> assertThat(found.isJdProvided()).isFalse(),
                () -> assertThat(found.isBillingRequired()).isFalse()
        );
    }

    @Test
    void 존재하지_않는_분석을_조회하면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resumeAnalysisService.readById(9_999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 이력서 분석입니다.");
    }

    @Test
    void JD가_없는_분석의_평가_결과를_복원하면_JD적합성은_null이고_종합점수는_4지표_가중치로_계산된다() {
        // given
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(null,
                new GuestInfo(UUID.randomUUID().toString(), new ClientIp("11.22.33.72"),
                        UUID.randomUUID().toString()),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITHOUT_JD, false);
        resumeAnalysisStateService.completeEvaluation(saved.getId(),
                ResumeAnalysisEvaluationFixture.withoutJd());

        // when
        ResumeAnalysisEvaluation evaluation = resumeAnalysisService.readEvaluation(saved.getId());

        // then
        assertAll(
                () -> assertThat(evaluation.jdFit()).isNull(),
                () -> assertThat(evaluation.problemSolving().score()).isEqualTo(90),
                () -> assertThat(evaluation.problemSolving().reason()).containsExactly("근거1", "근거2"),
                () -> assertThat(evaluation.softSkills().score()).isEqualTo(60),
                () -> assertThat(evaluation.totalScore()).isEqualTo(78),
                () -> assertThat(evaluation.totalFeedback()).isEqualTo("종합 총평")
        );
    }

    @Test
    void 평가가_완료되지_않은_분석의_평가_결과를_읽으면_예외가_발생한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITHOUT_JD, false);

        // when & then
        assertThatThrownBy(() -> resumeAnalysisService.readEvaluation(saved.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("평가가 완료되지 않은 이력서 분석입니다.");
    }

    @Test
    void 원문이_없는_분석의_원문을_읽으면_예외가_발생한다() {
        // given
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysis.forGuest(
                UUID.randomUUID().toString(), new ClientIp("11.22.33.73"), UUID.randomUUID().toString(),
                JOB_INPUT_WITHOUT_JD));

        // when & then
        assertAll(
                () -> assertThat(resumeAnalysisService.existsSourceText(analysis.getId())).isFalse(),
                () -> assertThatThrownBy(() -> resumeAnalysisService.readSourceText(analysis.getId()))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessageContaining("이력서 원문이 만료되어")
        );
    }

    @Test
    void 과금_CAS는_첫_호출에만_1행을_갱신하고_두_번째_호출은_0행이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITHOUT_JD, true);

        // when
        boolean first = resumeAnalysisService.markTokenCharged(saved.getId(),
                ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST);
        boolean second = resumeAnalysisService.markTokenCharged(saved.getId(),
                ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST);

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isFalse(),
                () -> assertThat(found.getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void 토큰_차감_실패를_기록하면_charged_token_count가_0으로_되돌아가고_실패_플래그가_남는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        ResumeAnalysis saved = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT_WITHOUT_JD, true);
        resumeAnalysisService.markTokenCharged(saved.getId(),
                ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST);

        // when
        resumeAnalysisService.markTokenChargeFailed(saved.getId());

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(found.getChargedTokenCount()).isZero(),
                () -> assertThat(found.isTokenChargeFailed()).isTrue()
        );
    }
}
