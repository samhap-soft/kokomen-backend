package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.CareerMaterialsPathResolver;
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
public class PdfUploadService {

    private final CareerMaterialsPathResolver careerMaterialsPathResolver;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final MemberResumeRepository memberResumeRepository;
    private final S3Service s3Service;

    @Transactional
    public MemberResume saveResume(byte[] resumeData, String filename, Member member, String content) {
        validateByteArray(resumeData);
        String s3Key = careerMaterialsPathResolver.resolveResumeS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolveResumeCdnPath(member.getId(), s3Key);

        MemberResume memberResume = new MemberResume(member, filename, cdnPath, content);
        MemberResume savedResume = memberResumeRepository.save(memberResume);

        uploadToS3IfNotExists(s3Key, resumeData);
        return savedResume;
    }

    @Transactional
    public MemberPortfolio savePortfolio(byte[] portfolioData, String filename, Member member, String content) {
        validateByteArray(portfolioData);
        String s3Key = careerMaterialsPathResolver.resolvePortfolioS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolvePortfolioCdnPath(member.getId(), s3Key);

        MemberPortfolio memberPortfolio = new MemberPortfolio(member, filename, cdnPath, content);
        MemberPortfolio savedPortfolio = memberPortfolioRepository.save(memberPortfolio);

        uploadToS3IfNotExists(s3Key, portfolioData);
        return savedPortfolio;
    }

    private void validateByteArray(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BadRequestException("파일이 비어있습니다.");
        }
    }

    private void uploadToS3IfNotExists(String s3Key, byte[] data) {
        if (s3Service.exists(s3Key)) {
            return;
        }
        s3Service.uploadS3File(s3Key, data, "application/pdf");
    }
}
