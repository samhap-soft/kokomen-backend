package com.samhap.kokomen.recruit.schedular.dto.mapper;

import com.samhap.kokomen.recruit.domain.Education;
import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public enum EducationMapper {

    ANY("무관", Education.ANY),
    HIGH_SCHOOL("고졸", Education.HIGH_SCHOOL),
    ASSOCIATE("전문대졸", Education.ASSOCIATE),
    BACHELOR("학사", Education.BACHELOR),
    MASTER("석사", Education.MASTER),
    DOCTORATE("박사", Education.DOCTORATE);

    private final String name;
    private final Education education;

    EducationMapper(String name, Education education) {
        this.name = name;
        this.education = education;
    }

    public static Education mapEducation(String koreanName) {
        if (koreanName == null || koreanName.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(education -> education.getName().equals(koreanName))
                .findFirst()
                .map(EducationMapper::getEducation)
                .orElseGet(() -> {
                    log.warn("Unknown education: {}", koreanName);
                    return null;
                });
    }
}
