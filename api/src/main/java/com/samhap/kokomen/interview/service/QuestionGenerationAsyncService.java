package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.external.ResumeBasedQuestionBedrockService;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.service.dto.ResumeBasedQuestionGenerateRequest;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class QuestionGenerationAsyncService {

    private final QuestionGenerationStateService questionGenerationStateService;
    private final ResumeContentService resumeContentService;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final ResumeBasedQuestionBedrockService resumeBasedQuestionBedrockService;
    private final PdfTextExtractor pdfTextExtractor;
    private final ThreadPoolTaskExecutor executor;

    public QuestionGenerationAsyncService(
            QuestionGenerationStateService questionGenerationStateService,
            ResumeContentService resumeContentService,
            MemberResumeRepository memberResumeRepository,
            MemberPortfolioRepository memberPortfolioRepository,
            ResumeBasedQuestionBedrockService resumeBasedQuestionBedrockService,
            PdfTextExtractor pdfTextExtractor,
            @Qualifier("gptCallbackExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.questionGenerationStateService = questionGenerationStateService;
        this.resumeContentService = resumeContentService;
        this.memberResumeRepository = memberResumeRepository;
        this.memberPortfolioRepository = memberPortfolioRepository;
        this.resumeBasedQuestionBedrockService = resumeBasedQuestionBedrockService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.executor = executor;
    }

    public void generateQuestionsAsync(
            Long generationId,
            ResumeBasedQuestionGenerateRequest request,
            Long memberId
    ) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                processQuestionGeneration(generationId, request, memberId);
            } catch (Exception e) {
                log.error("질문 생성 실패 - resumeBasedInterviewResultId: {}", generationId, e);
                questionGenerationStateService.markAsFailed(generationId);
            } finally {
                MDC.clear();
            }
        });
    }

    private void setMdcContext(Map<String, String> mdcContext) {
        if (mdcContext != null) {
            MDC.setContextMap(mdcContext);
        }
    }

    private void processQuestionGeneration(
            Long generationId,
            ResumeBasedQuestionGenerateRequest request,
            Long memberId
    ) {
        String resumeText = extractResumeText(memberId, request.resume(), request.resumeId());
        String portfolioText = extractPortfolioText(memberId, request.portfolio(), request.portfolioId());

        List<GeneratedQuestionDto> questions = resumeBasedQuestionBedrockService.generateQuestions(
                resumeText,
                portfolioText,
                request.jobCareer()
        );

        questionGenerationStateService.saveQuestionsAndComplete(generationId, questions);
    }

    private String extractResumeText(Long memberId, MultipartFile resumeFile, Long resumeId) {
        if (resumeFile != null && !resumeFile.isEmpty()) {
            return pdfTextExtractor.extractText(resumeFile);
        }
        if (resumeId != null) {
            MemberResume resume = memberResumeRepository.findByIdAndMemberId(resumeId, memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 이력서입니다."));
            return resumeContentService.getOrExtractResumeContent(resume);
        }
        throw new BadRequestException("이력서 파일 또는 이력서 ID가 필요합니다.");
    }

    private String extractPortfolioText(Long memberId, MultipartFile portfolioFile, Long portfolioId) {
        if (portfolioFile != null && !portfolioFile.isEmpty()) {
            return pdfTextExtractor.extractText(portfolioFile);
        }
        if (portfolioId != null) {
            MemberPortfolio portfolio = memberPortfolioRepository.findByIdAndMemberId(portfolioId, memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 포트폴리오입니다."));
            return resumeContentService.getOrExtractPortfolioContent(portfolio);
        }
        return null;
    }
}
