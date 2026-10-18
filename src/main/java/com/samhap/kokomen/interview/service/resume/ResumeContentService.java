package com.samhap.kokomen.interview.service.resume;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.tool.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 저장 자료 재사용 제출 경로의 텍스트 공급자다. content 컬럼이 비어 있을 때만 S3에서 PDF를 내려 추출하고
 * 결과를 그 컬럼에 캐시한다.
 *
 * <p>캐시는 넘겨받은 엔티티를 고쳐서가 아니라 리포지토리의 조건부 UPDATE로 쓴다. 제출 경로는 트랜잭션 없이
 * 자료를 읽어 넘기고 운영은 OSIV off라 이 엔티티는 준영속이므로, 값을 바꿔도 변경 감지가 저장해 주지 않는다.
 * 같은 이유로 이 클래스는 트랜잭션을 열지 않는다. 열어도 준영속 엔티티의 변경은 저장되지 않고, S3 다운로드와
 * PDF 추출 내내 커넥션만 붙잡는다. 캐시 쓰기는 추출 예외를 BadRequest로 바꾸는 try 밖에 둔다. 안에 두면
 * DB 오류까지 '텍스트 추출 실패'로 둔갑한다.
 *
 * <p>호출하는 쪽도 트랜잭션 밖에서 불러야 한다. 안에서 부르면 캐시 UPDATE의 행 락이 그 트랜잭션이 끝날 때까지 남고,
 * 뒤이은 saveAnalysis(REQUIRES_NEW)의 resume_analysis INSERT가 FK 검사로 그 행을 기다리다 lock wait timeout으로
 * 실패한다. 캐시는 content가 NULL인 행에만 쓴다. 빈 문자열이 저장된 행(텍스트가 없는 PDF)은 매번 다시 추출하지만
 * 같은 PDF라 결과도 같다.
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
    private final MemberResumeRepository memberResumeRepository;
    private final MemberPortfolioRepository memberPortfolioRepository;

    public String getOrExtractResumeContent(MemberResume resume) {
        if (resume.hasContent()) {
            return resume.getContent();
        }
        String extractedText = extractResumeText(resume);
        memberResumeRepository.updateContentIfAbsent(resume.getId(), extractedText);
        return extractedText;
    }

    private String extractResumeText(MemberResume resume) {
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(resume.getResumeUrl());
            return pdfTextExtractor.extractTextWithLinks(pdfBytes);
        } catch (Exception e) {
            log.error("이력서 PDF 다운로드/추출 실패 - resumeId: {}, url: {}",
                    resume.getId(), resume.getResumeUrl(), e);
            throw new BadRequestException("이력서에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    public String getOrExtractPortfolioContent(MemberPortfolio portfolio) {
        if (portfolio.hasContent()) {
            return portfolio.getContent();
        }
        String extractedText = extractPortfolioText(portfolio);
        memberPortfolioRepository.updateContentIfAbsent(portfolio.getId(), extractedText);
        return extractedText;
    }

    private String extractPortfolioText(MemberPortfolio portfolio) {
        try {
            byte[] pdfBytes = s3Service.downloadFileFromUrl(portfolio.getPortfolioUrl());
            return pdfTextExtractor.extractTextWithLinks(pdfBytes);
        } catch (Exception e) {
            log.error("포트폴리오 PDF 다운로드/추출 실패 - portfolioId: {}, url: {}",
                    portfolio.getId(), portfolio.getPortfolioUrl(), e);
            throw new BadRequestException("포트폴리오에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }
}
