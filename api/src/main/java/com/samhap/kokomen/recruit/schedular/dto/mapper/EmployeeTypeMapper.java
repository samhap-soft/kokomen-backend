package com.samhap.kokomen.recruit.schedular.dto.mapper;

import com.samhap.kokomen.recruit.domain.EmployeeType;
import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public enum EmployeeTypeMapper {

    CONVERSION_INTERN("전환형인턴", EmployeeType.CONVERSION_INTERN),
    EXPERIENCE_INTERN("체험형인턴", EmployeeType.EXPERIENCE_INTERN),
    FULL_TIME("정규직", EmployeeType.FULL_TIME),
    CONTRACT("계약직", EmployeeType.CONTRACT),
    MILITARY_SERVICE("병역특례", EmployeeType.MILITARY_SERVICE),
    DAILY("일용직", EmployeeType.DAILY),
    FREELANCER("프리랜서", EmployeeType.FREELANCER);

    private final String name;
    private final EmployeeType employeeType;

    EmployeeTypeMapper(String name, EmployeeType employeeType) {
        this.name = name;
        this.employeeType = employeeType;
    }

    public static EmployeeType mapEmployeeType(String koreanName) {
        if (koreanName == null || koreanName.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(type -> type.getName().equals(koreanName))
                .findFirst()
                .map(EmployeeTypeMapper::getEmployeeType)
                .orElseGet(() -> {
                    log.warn("Unknown employeeType: {}", koreanName);
                    return null;
                });
    }
}
