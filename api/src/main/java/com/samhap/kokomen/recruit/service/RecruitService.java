package com.samhap.kokomen.recruit.service;

import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Region;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RecruitService {

    public FiltersResponse getFilters() {
        List<String> deadlineTypes = Arrays.stream(DeadlineType.values())
                .map(DeadlineType::getName)
                .toList();
        List<String> educations = Arrays.stream(Education.values())
                .map(Education::getName)
                .toList();
        List<String> employeeTypes = Arrays.stream(EmployeeType.values())
                .map(EmployeeType::getName)
                .toList();
        List<String> employments = Arrays.stream(Employment.values())
                .map(Employment::getName)
                .toList();
        List<String> regions = Arrays.stream(Region.values())
                .map(Region::getName)
                .toList();
        return new FiltersResponse(
                deadlineTypes,
                educations,
                employeeTypes,
                employments,
                regions
        );
    }
}
