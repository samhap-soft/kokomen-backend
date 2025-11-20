package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CareerMaterialsFacadeService {

    private final ResumeService resumeService;
    private final PortfolioService portfolioService;
    private final MemberService memberService;

    public CareerMaterialsResponse getCareerMaterials(CareerMaterialsType type, MemberAuth memberAuth) {
        return switch (type) {
            case ALL:
                yield new CareerMaterialsResponse(
                        resumeService.getResumesByMemberId(memberAuth.memberId()),
                        portfolioService.getPortfoliosByMemberId(memberAuth.memberId())
                );
            case RESUME:
                yield new CareerMaterialsResponse(
                        resumeService.getResumesByMemberId(memberAuth.memberId()),
                        List.of()
                );
            case PORTFOLIO:
                yield new CareerMaterialsResponse(
                        List.of(),
                        portfolioService.getPortfoliosByMemberId(memberAuth.memberId())
                );
        };
    }

    public void saveCareerMaterials(ResumeSaveRequest request, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        resumeService.saveResume(request.resume(), member);
        if (request.portfolio() != null) {
            portfolioService.savePortfolio(request.portfolio(), member);
        }
    }
}
