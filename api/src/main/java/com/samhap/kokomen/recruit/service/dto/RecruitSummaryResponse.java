package com.samhap.kokomen.recruit.service.dto;

import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Recruit;
import com.samhap.kokomen.recruit.domain.Region;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record RecruitSummaryResponse(
        String id,
        AffiliateResponse affiliate,
        CompanyResponse company,
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

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static RecruitSummaryResponse from(
            Recruit recruit
    ) {
        return new RecruitSummaryResponse(
                recruit.getExternalId(),
                AffiliateResponse.from(recruit.getAffiliate()),
                CompanyResponse.from(recruit.getCompany()),
                recruit.getTitle(),
                formatEndDate(recruit.getEndDate()),
                recruit.getDeadlineType().getName(),
                recruit.getCareerMin(),
                recruit.getCareerMax(),
                recruit.getRegions().stream()
                        .map(Region::getName)
                        .toList(),
                recruit.getEmployeeTypes().stream()
                        .map(EmployeeType::getName)
                        .toList(),
                recruit.getEducations().stream()
                        .map(Education::getName)
                        .toList(),
                recruit.getEmployments().stream()
                        .map(Employment::getName)
                        .toList(),
                recruit.getUrl()
        );
    }

    private static String formatEndDate(LocalDateTime endDate) {
        if (endDate == null) {
            return null;
        }
        return endDate.format(ISO_FORMATTER);
    }
}
