package com.samhap.kokomen.resume.service.dto;

import java.util.List;

public record CareerMaterialsResponse(
        List<ResumeResponse> resumes,
        List<PortfolioResponse> portfolios
) {
}
