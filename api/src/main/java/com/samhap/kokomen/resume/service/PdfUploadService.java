package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.CareerMaterialsPathResolver;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@Service
public class PdfUploadService {

    private final CareerMaterialsPathResolver careerMaterialsPathResolver;
    private final PdfValidator pdfValidator;
    private final PdfTextExtractor pdfTextExtractor;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final S3Service s3Service;

    @Async
    @Transactional
    public void savePortfolio(MultipartFile portfolio, Member member, String content) {
        pdfValidator.validate(portfolio);
        String filename = portfolio.getOriginalFilename();
        String s3Key = careerMaterialsPathResolver.resolvePortfolioS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolvePortfolioCdnPath(member.getId(), s3Key);

        MemberPortfolio memberPortfolio = new MemberPortfolio(member, filename, cdnPath, content);
        memberPortfolioRepository.save(memberPortfolio);

        uploadToS3IfNotExists(s3Key, portfolio);
    }

    // TODO: 이력서 평가가 비동기로 전환 완료되면 삭제하기
    @Async
    @Transactional
    public void savePortfolio(MultipartFile portfolio, Member member) {
        String content = extractTextSafely(portfolio);
        savePortfolio(portfolio, member, content);
    }

    @Async
    @Transactional
    public void saveResume(MultipartFile resume, Member member, String content) {
        pdfValidator.validate(resume);
        String filename = resume.getOriginalFilename();
        String s3Key = careerMaterialsPathResolver.resolveResumeS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolveResumeCdnPath(member.getId(), s3Key);

        MemberResume memberResume = new MemberResume(member, filename, cdnPath, content);
        memberResumeRepository.save(memberResume);

        uploadToS3IfNotExists(s3Key, resume);
    }

    // TODO: 이력서 평가가 비동기로 전환 완료되면 삭제하기
    @Async
    @Transactional
    public void saveResume(MultipartFile resume, Member member) {
        String content = extractTextSafely(resume);
        saveResume(resume, member, content);
    }

    private String extractTextSafely(MultipartFile file) {
        try {
            return pdfTextExtractor.extractText(file);
        } catch (Exception e) {
            log.warn("PDF 텍스트 추출 실패 (업로드는 계속 진행): {}", e.getMessage());
            return null;
        }
    }

    private void uploadToS3IfNotExists(String s3Key, MultipartFile file) {
        if (s3Service.exists(s3Key)) {
            return;
        }
        try {
            s3Service.uploadS3File(s3Key, file.getBytes(), "application/pdf");
        } catch (Exception e) {
            throw new BadRequestException("파일 업로드에 실패했습니다.");
        }
    }
}
