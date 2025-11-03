package com.samhap.kokomen.recruit.domain;

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
}
