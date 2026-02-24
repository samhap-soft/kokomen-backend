package com.samhap.kokomen.recruit.schedular.dto.mapper;

import com.samhap.kokomen.recruit.domain.DeadlineType;
import java.util.Arrays;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public enum DeadlineTypeMapper {

    ALWAYS_OPEN("상시채용", DeadlineType.ALWAYS_OPEN),
    UNTIL_HIRED("채용시마감", DeadlineType.UNTIL_HIRED),
    DEADLINE_SET("마감일", DeadlineType.DEADLINE_SET);

    private final String name;
    private final DeadlineType deadlineType;

    DeadlineTypeMapper(String name, DeadlineType deadlineType) {
        this.name = name;
        this.deadlineType = deadlineType;
    }

    public static DeadlineType mapDeadlineType(String koreanName) {
        if (koreanName == null || koreanName.isBlank()) {
            return DeadlineType.DEADLINE_SET;
        }

        return Arrays.stream(values())
                .filter(type -> type.getName().equals(koreanName))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown deadline type: {}, using default DEADLINE_SET", koreanName);
                    return DeadlineTypeMapper.DEADLINE_SET;
                })
                .getDeadlineType();
    }
}
