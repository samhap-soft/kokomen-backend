package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.repository.MemberPortfolioRepository;
import com.samhap.kokomen.resume.repository.MemberResumeRepository;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import com.samhap.kokomen.resume.service.dto.PortfolioResponse;
import com.samhap.kokomen.resume.service.dto.ResumeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CareerMaterialsService {

    private final MemberPortfolioRepository memberPortfolioRepository;
    private final MemberResumeRepository memberResumeRepository;

    public CareerMaterialsResponse getCareerMaterials(CareerMaterialsType type, MemberAuth memberAuth) {
        return switch (type) {
            case ALL:
                yield new CareerMaterialsResponse(
                        getResumesByMemberId(memberAuth.memberId()),
                        getPortfoliosByMemberId(memberAuth.memberId())
                );
            case RESUME:
                yield new CareerMaterialsResponse(
                        getResumesByMemberId(memberAuth.memberId()),
                        List.of()
                );
            case PORTFOLIO:
                yield new CareerMaterialsResponse(
                        List.of(),
                        getPortfoliosByMemberId(memberAuth.memberId())
                );
        };
    }

    private List<ResumeResponse> getResumesByMemberId(Long memberId) {
        return memberResumeRepository.findByMemberId(memberId).stream()
                .map(resume -> new ResumeResponse(
                        resume.getId(),
                        resume.getTitle(),
                        resume.getResumeUrl(),
                        resume.getCreatedAt()
                ))
                .toList();
    }

    private List<PortfolioResponse> getPortfoliosByMemberId(Long memberId) {
        return memberPortfolioRepository.findByMemberId(memberId).stream()
                .map(portfolio -> new PortfolioResponse(
                        portfolio.getId(),
                        portfolio.getTitle(),
                        portfolio.getPortfolioUrl(),
                        portfolio.getCreatedAt()
                ))
                .toList();
    }
}
