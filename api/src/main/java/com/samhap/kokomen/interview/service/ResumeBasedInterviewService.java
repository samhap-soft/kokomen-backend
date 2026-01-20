package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.ResumeQuestionGenerationState;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.ResumeQuestionGenerationRepository;
import com.samhap.kokomen.interview.service.dto.GeneratedQuestionsResponse;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationStateResponse;
import com.samhap.kokomen.interview.service.dto.QuestionGenerationSubmitResponse;
import com.samhap.kokomen.interview.service.dto.ResumeBasedQuestionGenerateRequest;
import com.samhap.kokomen.interview.service.dto.ResumeQuestionGenerationPageResponse;
import com.samhap.kokomen.interview.service.dto.ResumeQuestionGenerationResponse;
import com.samhap.kokomen.interview.service.dto.ResumeQuestionUsageStatusResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeBasedInterviewService {

    private static final int RESUME_QUESTION_GENERATION_TOKEN_COST = 5;

    private final ResumeQuestionGenerationRepository resumeQuestionGenerationRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final MemberRepository memberRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final QuestionGenerationAsyncService questionGenerationAsyncService;
    private final TokenFacadeService tokenFacadeService;

    @Transactional
    public QuestionGenerationSubmitResponse submitQuestionGeneration(
            Long memberId,
            ResumeBasedQuestionGenerateRequest request
    ) {
        if (!isFirstUse(memberId)) {
            tokenFacadeService.useTokens(memberId, RESUME_QUESTION_GENERATION_TOKEN_COST);
        }

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

    @Transactional(readOnly = true)
    public ResumeQuestionUsageStatusResponse getUsageStatus(Long memberId) {
        return ResumeQuestionUsageStatusResponse.of(isFirstUse(memberId));
    }

    private boolean isFirstUse(Long memberId) {
        return !resumeQuestionGenerationRepository.existsByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public ResumeQuestionGenerationPageResponse findMyQuestionGenerations(
            Long memberId,
            ResumeQuestionGenerationState state,
            Pageable pageable
    ) {
        Page<ResumeQuestionGeneration> page = getResumeQuestionGenerations(memberId, state, pageable);

        List<ResumeQuestionGenerationResponse> data = page.getContent().stream()
                .map(ResumeQuestionGenerationResponse::from)
                .toList();
        return ResumeQuestionGenerationPageResponse.of(data, page);
    }

    private Page<ResumeQuestionGeneration> getResumeQuestionGenerations(
            Long memberId,
            ResumeQuestionGenerationState state,
            Pageable pageable
    ) {
        if (state == null) {
            List<ResumeQuestionGenerationState> defaultStates = List.of(
                    ResumeQuestionGenerationState.PENDING,
                    ResumeQuestionGenerationState.COMPLETED
            );
            return resumeQuestionGenerationRepository.findByMemberIdAndStateIn(memberId, defaultStates, pageable);
        }
        return resumeQuestionGenerationRepository.findByMemberIdAndState(memberId, state, pageable);
    }

    @Transactional(readOnly = true)
    public QuestionGenerationStateResponse getQuestionGenerationStatus(Long generationId, Long memberId) {
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.findById(generationId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 질문 생성 요청입니다."));
        if (!generation.isOwner(memberId)) {
            throw new ForbiddenException("본인의 질문 생성 요청만 조회할 수 있습니다.");
        }
        return QuestionGenerationStateResponse.of(generation.getState());
    }

    @Transactional(readOnly = true)
    public List<GeneratedQuestionsResponse> getGeneratedQuestions(Long generationId, Long memberId) {
        ResumeQuestionGeneration generation = resumeQuestionGenerationRepository.findById(generationId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 질문 생성 요청입니다."));
        if (!generation.isOwner(memberId)) {
            throw new ForbiddenException("본인의 질문 생성 요청만 조회할 수 있습니다.");
        }
        if (!generation.isCompleted()) {
            throw new BadRequestException("질문 생성이 완료되지 않았습니다.");
        }

        List<GeneratedQuestion> questions = generatedQuestionRepository.findByGenerationIdOrderByQuestionOrder(
                generationId);
        return questions.stream()
                .map(GeneratedQuestionsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeQuestionGeneration readGeneration(Long generationId) {
        return resumeQuestionGenerationRepository.findById(generationId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 질문 생성 요청입니다."));
    }

    @Transactional(readOnly = true)
    public GeneratedQuestion readGeneratedQuestion(Long questionId, Long generationId) {
        GeneratedQuestion question = generatedQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 생성 질문입니다."));
        if (!question.getGeneration().getId().equals(generationId)) {
            throw new BadRequestException("해당 질문 생성 요청에 속하지 않는 질문입니다.");
        }
        return question;
    }
}
