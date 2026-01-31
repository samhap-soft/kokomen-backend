package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeContentService {

    private final S3Service s3Service;
    private final PdfTextExtractor pdfTextExtractor;

    @Transactional
    public String getOrExtractResumeContent(MemberResume resume) {
        if (resume.hasContent()) {
            return resume.getContent();
        }
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(resume.getResumeUrl());
            String extractedText = pdfTextExtractor.extractText(pdfBytes);
            resume.updateContent(extractedText);
            return extractedText;
        } catch (Exception e) {
            log.error("이력서 PDF 다운로드/추출 실패 - resumeId: {}, url: {}",
                    resume.getId(), resume.getResumeUrl(), e);
            throw new BadRequestException("이력서에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    @Transactional
    public String getOrExtractPortfolioContent(MemberPortfolio portfolio) {
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
}
