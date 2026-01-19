package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberPortfolioFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.MemberResumeFixtureBuilder;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import com.samhap.kokomen.interview.repository.ResumeQuestionGenerationRepository;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationSubmitResponse;
import com.samhap.kokomen.interview.service.dto.ResumeBasedQuestionGenerateRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ResumeBasedInterviewServiceTest extends BaseTest {

    @Autowired
    private ResumeBasedInterviewService resumeBasedInterviewService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberResumeRepository memberResumeRepository;

    @Autowired
    private MemberPortfolioRepository memberPortfolioRepository;

    @Autowired
    private ResumeQuestionGenerationRepository resumeQuestionGenerationRepository;

    @Test
    void 기존_이력서_ID로_질문_생성을_요청하면_ResumeQuestionGeneration이_생성되고_비동기_처리가_시작된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용입니다. Java, Spring Boot 경험 있습니다.")
                        .build()
        );

        // when
        ResumeBasedQuestionGenerateRequest request = new ResumeBasedQuestionGenerateRequest(
                null, null, resume.getId(), null, "신입"
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        assertThat(response.resumeBasedInterviewResultId()).isNotNull();

        // DB 검증 - ResumeQuestionGeneration이 PENDING 상태로 생성되었는지 확인
        ResumeQuestionGeneration savedGeneration = resumeQuestionGenerationRepository.findById(
                response.resumeBasedInterviewResultId()).orElseThrow();
        assertAll(
                () -> assertThat(savedGeneration.getState()).isEqualTo(ResumeQuestionGenerationState.PENDING),
                () -> assertThat(savedGeneration.getMemberResume()).isNotNull(),
                () -> assertThat(savedGeneration.getJobCareer()).isEqualTo("신입")
        );

        // 비동기 서비스 호출 검증
        verify(questionGenerationAsyncService).generateQuestionsAsync(
                anyLong(), any(ResumeBasedQuestionGenerateRequest.class), anyLong()
        );
    }

    @Test
    void 이력서와_포트폴리오_ID로_질문_생성을_요청할_수_있다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용입니다.")
                        .build()
        );
        MemberPortfolio portfolio = memberPortfolioRepository.save(
                MemberPortfolioFixtureBuilder.builder()
                        .member(member)
                        .content("포트폴리오 내용입니다.")
                        .build()
        );

        // when
        ResumeBasedQuestionGenerateRequest request = new ResumeBasedQuestionGenerateRequest(
                null, null, resume.getId(), portfolio.getId(), "경력 3년"
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        ResumeQuestionGeneration savedGeneration = resumeQuestionGenerationRepository.findById(
                response.resumeBasedInterviewResultId()).orElseThrow();
        assertAll(
                () -> assertThat(savedGeneration.getMemberResume()).isNotNull(),
                () -> assertThat(savedGeneration.getMemberPortfolio()).isNotNull(),
                () -> assertThat(savedGeneration.getJobCareer()).isEqualTo("경력 3년")
        );
    }

    @Test
    void 이력서_파일과_ID_모두_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ResumeBasedQuestionGenerateRequest(
                null, null, null, null, "신입"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이력서 파일 또는 이력서 ID는 필수입니다.");
    }
}
