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
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.repository.InterviewRepository;
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
    private InterviewRepository interviewRepository;

    @Test
    void 기존_이력서_ID로_질문_생성을_요청하면_Interview가_생성되고_비동기_처리가_시작된다() {
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
                null, null, resume.getId(), null, "신입", 3
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        assertThat(response.interviewId()).isNotNull();

        // DB 검증 - Interview가 GENERATING_QUESTIONS 상태로 생성되었는지 확인
        Interview savedInterview = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertAll(
                () -> assertThat(savedInterview.getInterviewType()).isEqualTo(InterviewType.RESUME_BASED),
                () -> assertThat(savedInterview.getInterviewState()).isEqualTo(InterviewState.GENERATING_QUESTIONS),
                () -> assertThat(savedInterview.getMemberResume()).isNotNull(),
                () -> assertThat(savedInterview.getJobCareer()).isEqualTo("신입"),
                () -> assertThat(savedInterview.getMaxQuestionCount()).isEqualTo(3)
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
                null, null, resume.getId(), portfolio.getId(), "경력 3년", 3
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        Interview savedInterview = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertAll(
                () -> assertThat(savedInterview.getMemberResume()).isNotNull(),
                () -> assertThat(savedInterview.getMemberPortfolio()).isNotNull(),
                () -> assertThat(savedInterview.getJobCareer()).isEqualTo("경력 3년")
        );
    }

    @Test
    void 이력서_파일과_ID_모두_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> new ResumeBasedQuestionGenerateRequest(
                null, null, null, null, "신입", 3
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이력서 파일 또는 이력서 ID는 필수입니다.");
    }

    @Test
    void 질문_개수를_지정하지_않으면_기본값_3개가_적용된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용")
                        .build()
        );

        // when
        ResumeBasedQuestionGenerateRequest request = new ResumeBasedQuestionGenerateRequest(
                null, null, resume.getId(), null, "신입", null
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        Interview savedInterview = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(savedInterview.getMaxQuestionCount()).isEqualTo(3);
    }

    @Test
    void 질문_개수가_최대값_5를_초과하면_5개로_제한된다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MemberResume resume = memberResumeRepository.save(
                MemberResumeFixtureBuilder.builder()
                        .member(member)
                        .content("이력서 내용")
                        .build()
        );

        // when
        ResumeBasedQuestionGenerateRequest request = new ResumeBasedQuestionGenerateRequest(
                null, null, resume.getId(), null, "신입", 10
        );
        QuestionGenerationSubmitResponse response = resumeBasedInterviewService.submitQuestionGeneration(
                member.getId(), request
        );

        // then
        Interview savedInterview = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(savedInterview.getMaxQuestionCount()).isEqualTo(5);
    }
}
