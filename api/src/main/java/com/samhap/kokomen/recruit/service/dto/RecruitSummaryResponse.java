package com.samhap.kokomen.recruit.service.dto;

import java.util.List;

public record RecruitSummaryResponse(
        String id,
        AffiliateResponse affiliate,
        CompanyResponse companyResponse,
        String title,
        String endDate,
        String deadlineType,
        Integer careerMin,
        Integer careerMax,
        List<String> region,
        List<String> employeeType,
        List<String> education,
        List<String> employment,
        String url
) {
}
