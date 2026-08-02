package com.samhap.kokomen.interview.service.resume;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 자료 재사용 제출 경로의 텍스트 공급자다. content 컬럼이 비어 있을 때만 S3에서 PDF를 내려 추출하고
 * 결과를 캐시한다.
 *
 * <p>추출은 반드시 {@code extractTextWithLinks}로 한다. 파일 업로드 경로
 * ({@code ResumeAnalysisFacadeService})가 같은 메서드를 쓰므로, 여기서 링크를 뺀 추출을 쓰면 같은 이력서가
 * {@code resume_id}로 제출됐는지 파일로 제출됐는지에 따라 LLM 입력이 달라지고 technical_skills 점수가
 * 갈린다. 두 경로의 출력 일치는 {@code ResumeAnalysisFacadeServiceTest}가 같은 PDF를 두 방식으로 제출해
 * 고정한다.
 */
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
            String extractedText = pdfTextExtractor.extractTextWithLinks(pdfBytes);
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
            String extractedText = pdfTextExtractor.extractTextWithLinks(pdfBytes);
            portfolio.updateContent(extractedText);
            return extractedText;
        } catch (Exception e) {
            log.error("포트폴리오 PDF 다운로드/추출 실패 - portfolioId: {}, url: {}",
                    portfolio.getId(), portfolio.getPortfolioUrl(), e);
            throw new BadRequestException("포트폴리오에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }
}
