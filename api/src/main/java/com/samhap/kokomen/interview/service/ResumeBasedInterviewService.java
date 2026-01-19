package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.repository.ResumeQuestionGenerationRepository;
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

    private final ResumeQuestionGenerationRepository resumeQuestionGenerationRepository;
    private final MemberRepository memberRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final QuestionGenerationAsyncService questionGenerationAsyncService;

    @Transactional
    public QuestionGenerationSubmitResponse submitQuestionGeneration(
            Long memberId,
            ResumeBasedQuestionGenerateRequest request
    ) {
        Member member = readMember(memberId);
        MemberResume memberResume = findMemberResume(memberId, request.resumeId());
        MemberPortfolio memberPortfolio = findMemberPortfolio(memberId, request.portfolioId());

        ResumeQuestionGeneration generation = new ResumeQuestionGeneration(
                member,
                memberResume,
                memberPortfolio,
                request.jobCareer()
        );
        ResumeQuestionGeneration savedGeneration = resumeQuestionGenerationRepository.save(generation);

        questionGenerationAsyncService.generateQuestionsAsync(
                savedGeneration.getId(),
                request,
                memberId
        );

        return new QuestionGenerationSubmitResponse(savedGeneration.getId());
    }

    @Transactional(readOnly = true)
    public QuestionGenerationStatusResponse getQuestionGenerationStatus(Long generationId, Long memberId) {
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.findById(generationId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 질문 생성 요청입니다."));
        if (!generation.isOwner(memberId)) {
            throw new ForbiddenException("본인의 질문 생성 요청만 조회할 수 있습니다.");
        }
        return QuestionGenerationStatusResponse.of(generation.getState());
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
}
