package com.samhap.kokomen.recruit.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum DeadlineType {

    ALWAYS_OPEN("상시 채용"),
    UNTIL_HIRED("채용 시 마감"),
    DEADLINE_SET("기한 설정");

    private final String name;

    DeadlineType(String name) {
        this.name = name;
    }

    public static DeadlineType fromByName(String name) {
        return Arrays.stream(values())
                .filter(type -> type.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Invalid DeadlineType name: " + name));
    }

    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(DeadlineType::getName)
                .toList();
    }
}
