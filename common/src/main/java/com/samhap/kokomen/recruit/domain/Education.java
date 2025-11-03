package com.samhap.kokomen.recruit.domain;

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
}
