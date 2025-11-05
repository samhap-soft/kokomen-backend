package com.samhap.kokomen.recruit.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum Education {

    ANY("학력 무관"),
    HIGH_SCHOOL("고졸"),
    ASSOCIATE("초대졸"),
    BACHELOR("학사"),
    MASTER("석사"),
    DOCTORATE("박사");

    private final String name;

    Education(String name) {
        this.name = name;
    }

    public static Education findByName(String name) {
        return Arrays.stream(values())
                .filter(education -> education.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No enum constant with name: " + name));
    }

    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(Education::getName)
                .toList();
    }
}
