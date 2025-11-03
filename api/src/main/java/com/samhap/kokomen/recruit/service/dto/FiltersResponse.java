package com.samhap.kokomen.recruit.service.dto;

import java.util.List;

public record FiltersResponse(
        List<String> deadlineType,
        List<String> education,
        List<String> employeeType,
        List<String> employment,
        List<String> region
) {
}
