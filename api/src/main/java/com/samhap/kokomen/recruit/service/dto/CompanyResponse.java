package com.samhap.kokomen.recruit.service.dto;

import com.samhap.kokomen.recruit.domain.Company;

public record CompanyResponse(
        String id,
        String name,
        String image
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getImage()
        );
    }
}
