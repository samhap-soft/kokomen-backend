package com.samhap.kokomen.recruit.service;

import com.samhap.kokomen.recruit.domain.DeadlineType;
import com.samhap.kokomen.recruit.domain.Education;
import com.samhap.kokomen.recruit.domain.EmployeeType;
import com.samhap.kokomen.recruit.domain.Employment;
import com.samhap.kokomen.recruit.domain.Region;
import com.samhap.kokomen.recruit.service.dto.FiltersResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RecruitService {

    public FiltersResponse getFilters() {
        List<String> deadlineTypes = DeadlineType.getNames();
        List<String> educations = Education.getNames();
        List<String> employeeTypes = EmployeeType.getNames();
        List<String> employments = Employment.getNames();
        List<String> regions = Region.getNames();

        return new FiltersResponse(
                deadlineTypes,
                educations,
                employeeTypes,
                employments,
                regions
        );
    }
}
