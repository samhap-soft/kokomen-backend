package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.resume.domain.CareerMaterialsPathResolver;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.service.dto.ResumeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
public class ResumeService {

    private final CareerMaterialsPathResolver careerMaterialsPathResolver;
    private final PdfValidator pdfValidator;
    private final MemberResumeRepository memberResumeRepository;
    private final S3Service s3Service;

    public List<ResumeResponse> getResumesByMemberId(Long memberId) {
        return memberResumeRepository.findByMemberId(memberId).stream()
                .map(resume -> new ResumeResponse(
                        resume.getId(),
                        resume.getTitle(),
                        resume.getResumeUrl(),
                        resume.getCreatedAt()
                ))
                .toList();
    }

    @Async
    @Transactional
    public void saveResume(MultipartFile resume, Member member) {
        pdfValidator.validate(resume);
        String filename = resume.getOriginalFilename();
        String s3Key = careerMaterialsPathResolver.resolveResumeS3Key(member.getId(), filename);
        String cdnPath = careerMaterialsPathResolver.resolveResumeCdnPath(member.getId(), filename);

        MemberResume memberResume = new MemberResume(member, filename, cdnPath);
        memberResumeRepository.save(memberResume);

        if (s3Service.exists(s3Key)) {
            return;
        }
        try {
            s3Service.uploadS3File(s3Key, resume.getBytes(), "application/pdf");
        } catch (Exception e) {
            throw new BadRequestException("이력서 파일 업로드에 실패했습니다.");
        }
    }
}
