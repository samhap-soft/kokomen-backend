package com.samhap.kokomen.recruit.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum EmployeeType {

    CONVERSION_INTERN("전환형 인턴"),
    EXPERIENCE_INTERN("체험형 인턴"),
    FULL_TIME("정규직"),
    CONTRACT("계약직"),
    MILITARY_SERVICE("병역특례"),
    DAILY("일용직"),
    FREELANCER("프리랜서");

    private final String name;

    EmployeeType(String name) {
        this.name = name;
    }

    public static EmployeeType findByName(String name) {
        return Arrays.stream(values())
                .filter(employeeType -> employeeType.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No enum constant with name: " + name));
    }

    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(EmployeeType::getName)
                .toList();
    }
}
