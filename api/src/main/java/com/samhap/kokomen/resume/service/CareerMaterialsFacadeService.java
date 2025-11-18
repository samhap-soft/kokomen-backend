package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CareerMaterialsFacadeService {

    private final ResumeService resumeService;
    private final PortfolioService portfolioService;
    private final MemberService memberService;

    @Transactional
    public void saveResume(ResumeSaveRequest request, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        resumeService.saveResume(request.resume(), member);
        if (request.portfolio() != null) {
            portfolioService.savePortfolio(request.portfolio(), member);
        }
    }
}
