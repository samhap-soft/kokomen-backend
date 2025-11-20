package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.CareerMaterialsPathResolver;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.service.dto.PortfolioResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
public class PortfolioService {

    private final CareerMaterialsPathResolver careerMaterialsPathResolver;
    private final PdfValidator pdfValidator;
    private final MemberPortfolioRepository memberPortfolioRepository;
    private final S3Service s3Service;

    public List<PortfolioResponse> getPortfoliosByMemberId(Long memberId) {
        return memberPortfolioRepository.findByMemberId(memberId).stream()
                .map(portfolio -> new PortfolioResponse(
                        portfolio.getId(),
                        portfolio.getTitle(),
                        portfolio.getPortfolioUrl(),
                        portfolio.getCreatedAt()
                ))
                .toList();
    }

    @Async
    @Transactional
    public void savePortfolio(MultipartFile portfolio, Member member) {
        pdfValidator.validate(portfolio);
        String filename = portfolio.getOriginalFilename();
        String s3Key = careerMaterialsPathResolver.resolvePortfolioS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolvePortfolioCdnPath(member.getId(), filename);

        MemberPortfolio memberPortfolio = new MemberPortfolio(member, filename, cdnPath);
        memberPortfolioRepository.save(memberPortfolio);

        if (s3Service.exists(s3Key)) {
            return;
        }
        try {
            s3Service.uploadS3File(s3Key, portfolio.getBytes(), "application/pdf");
        } catch (Exception e) {
            throw new BadRequestException("포트폴리오 파일 업로드에 실패했습니다.");
        }
    }
}
