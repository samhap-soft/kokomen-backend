package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
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

    private static final int DEFAULT_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 5;

    private final InterviewStateService interviewStateService;
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final ResumeBasedQuestionBedrockService resumeBasedQuestionBedrockService;
    private final PdfTextExtractor pdfTextExtractor;
    private final S3Service s3Service;
    private final ThreadPoolTaskExecutor executor;

    public QuestionGenerationAsyncService(
            InterviewStateService interviewStateService,
            MemberResumeRepository memberResumeRepository,
            MemberPortfolioRepository memberPortfolioRepository,
            ResumeBasedQuestionBedrockService resumeBasedQuestionBedrockService,
            PdfTextExtractor pdfTextExtractor,
            S3Service s3Service,
            @Qualifier("gptCallbackExecutor")
            ThreadPoolTaskExecutor executor
    ) {
        this.interviewStateService = interviewStateService;
        this.memberResumeRepository = memberResumeRepository;
        this.memberPortfolioRepository = memberPortfolioRepository;
        this.resumeBasedQuestionBedrockService = resumeBasedQuestionBedrockService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.s3Service = s3Service;
        this.executor = executor;
    }

    public void generateQuestionsAsync(
            Long interviewId,
            ResumeBasedQuestionGenerateRequest request,
            Long memberId
    ) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        executor.execute(() -> {
            try {
                setMdcContext(mdcContext);
                processQuestionGeneration(interviewId, request, memberId);
            } catch (Exception e) {
                log.error("질문 생성 실패 - interviewId: {}", interviewId, e);
                interviewStateService.markAsFailed(interviewId);
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
            Long interviewId,
            ResumeBasedQuestionGenerateRequest request,
            Long memberId
    ) {
        String resumeText = extractResumeText(memberId, request.resume(), request.resumeId());
        String portfolioText = extractPortfolioText(memberId, request.portfolio(), request.portfolioId());
        int questionCount = calculateQuestionCount(request.questionCount());

        List<GeneratedQuestionDto> questions = resumeBasedQuestionBedrockService.generateQuestions(
                resumeText,
                portfolioText,
                request.jobCareer(),
                questionCount
        );

        interviewStateService.saveQuestionsAndComplete(interviewId, questions);
    }

    private String extractResumeText(Long memberId, MultipartFile resumeFile, Long resumeId) {
        if (resumeFile != null && !resumeFile.isEmpty()) {
            return pdfTextExtractor.extractText(resumeFile);
        }
        if (resumeId != null) {
            MemberResume resume = memberResumeRepository.findByIdAndMemberId(resumeId, memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 이력서입니다."));
            return getTextFromResume(resume);
        }
        throw new BadRequestException("이력서 파일 또는 이력서 ID가 필요합니다.");
    }

    private String getTextFromResume(MemberResume resume) {
        if (resume.hasContent()) {
            return resume.getContent();
        }
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(resume.getResumeUrl());
            String extractedText = pdfTextExtractor.extractText(pdfBytes);
            resume.updateContent(extractedText);
            return extractedText;
        } catch (Exception e) {
            log.error("이력서 PDF 다운로드/추출 실패 - resumeId: {}, url: {}", resume.getId(), resume.getResumeUrl(), e);
            throw new BadRequestException("이력서에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    private String extractPortfolioText(Long memberId, MultipartFile portfolioFile, Long portfolioId) {
        if (portfolioFile != null && !portfolioFile.isEmpty()) {
            return pdfTextExtractor.extractText(portfolioFile);
        }
        if (portfolioId != null) {
            MemberPortfolio portfolio = memberPortfolioRepository.findByIdAndMemberId(portfolioId, memberId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 포트폴리오입니다."));
            return getTextFromPortfolio(portfolio);
        }
        return null;
    }

    private String getTextFromPortfolio(MemberPortfolio portfolio) {
        if (portfolio.hasContent()) {
            return portfolio.getContent();
        }
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(portfolio.getPortfolioUrl());
            String extractedText = pdfTextExtractor.extractText(pdfBytes);
            portfolio.updateContent(extractedText);
            return extractedText;
        } catch (Exception e) {
            log.error("포트폴리오 PDF 다운로드/추출 실패 - portfolioId: {}, url: {}",
                    portfolio.getId(), portfolio.getPortfolioUrl(), e);
            throw new BadRequestException("포트폴리오에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    private int calculateQuestionCount(Integer questionCount) {
        if (questionCount == null) {
            return DEFAULT_QUESTION_COUNT;
        }
        return Math.min(questionCount, MAX_QUESTION_COUNT);
    }
}
