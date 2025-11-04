package com.samhap.kokomen.recruit.service.dto;

import com.samhap.kokomen.recruit.domain.Recruit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

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
                recruit.getId(),
                AffiliateResponse.from(recruit.getAffiliate()),
                CompanyResponse.from(recruit.getCompany()),
                recruit.getTitle(),
                formatEndDate(recruit.getEndDate()),
                recruit.getDeadlineType().name(),
                recruit.getCareerMin(),
                recruit.getCareerMax(),
                convertEnumsToNames(recruit.getRegions()),
                convertEnumsToNames(recruit.getEmployeeTypes()),
                convertEnumsToNames(recruit.getEducations()),
                convertEnumsToNames(recruit.getEmployments()),
                recruit.getUrl()
        );
    }

    private static String formatEndDate(LocalDateTime endDate) {
        if (endDate == null) {
            return null;
        }
        return endDate.format(ISO_FORMATTER);
    }

    private static <T extends Enum<T>> List<String> convertEnumsToNames(Set<T> enums) {
        return enums.stream()
                .map(Enum::name)
                .toList();
    }
}
