package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.ResumeBasedRootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationStatusResponse;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationSubmitResponse;
import com.samhap.kokomen.interview.service.dto.ResumeBasedQuestionGenerateRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeBasedInterviewService {

    private static final int DEFAULT_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 5;

    private final InterviewRepository interviewRepository;
    private final MemberRepository memberRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final ResumeBasedRootQuestionRepository resumeBasedRootQuestionRepository;
    private final QuestionGenerationAsyncService questionGenerationAsyncService;

    @Transactional
    public QuestionGenerationSubmitResponse submitQuestionGeneration(
            Long memberId,
            ResumeBasedQuestionGenerateRequest request
    ) {
        Member member = readMember(memberId);
        MemberResume memberResume = findMemberResume(memberId, request.resumeId());
        MemberPortfolio memberPortfolio = findMemberPortfolio(memberId, request.portfolioId());
        int questionCount = calculateQuestionCount(request.questionCount());

        Interview interview = Interview.createResumeBasedInterview(
                member,
                memberResume,
                memberPortfolio,
                request.jobCareer(),
                questionCount
        );
        Interview savedInterview = interviewRepository.save(interview);

        questionGenerationAsyncService.generateQuestionsAsync(
                savedInterview.getId(),
                request,
                memberId
        );

        return new QuestionGenerationSubmitResponse(savedInterview.getId());
    }

    @Transactional(readOnly = true)
    public QuestionGenerationStatusResponse getQuestionGenerationStatus(Long interviewId, Long memberId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));

        if (!interview.isInterviewee(memberId)) {
            throw new ForbiddenException("본인의 인터뷰만 조회할 수 있습니다.");
        }

        InterviewState status = interview.getInterviewState();

        if (status == InterviewState.PENDING) {
            int questionCount = resumeBasedRootQuestionRepository.countByInterviewId(interviewId);
            return QuestionGenerationStatusResponse.of(status, questionCount);
        }

        return QuestionGenerationStatusResponse.of(status);
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private MemberResume findMemberResume(Long memberId, Long resumeId) {
        if (resumeId == null) {
            return null;
        }
        return memberResumeRepository.findByIdAndMemberId(resumeId, memberId).orElse(null);
    }

    private MemberPortfolio findMemberPortfolio(Long memberId, Long portfolioId) {
        if (portfolioId == null) {
            return null;
        }
        return memberPortfolioRepository.findByIdAndMemberId(portfolioId, memberId).orElse(null);
    }

    private int calculateQuestionCount(Integer questionCount) {
        if (questionCount == null) {
            return DEFAULT_QUESTION_COUNT;
        }
        return Math.min(questionCount, MAX_QUESTION_COUNT);
    }
}
