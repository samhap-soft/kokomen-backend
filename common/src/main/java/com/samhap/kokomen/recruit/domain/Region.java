package com.samhap.kokomen.recruit.domain;

import lombok.Getter;

@Getter
public enum Region {

    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    BUSAN("부산"),
    DAEGU("대구"),
    GWANGJU("광주"),
    DAEJEON("대전"),
    ULSAN("울산"),
    SEJONG("세종"),
    GANGWON("강원"),
    GYEONGNAM("경남"),
    GYEONGBUK("경북"),
    JEONNAM("전남"),
    JEONBUK("전북"),
    CHUNGNAM("충남"),
    CHUNGBUK("충북"),
    JEJU("제주"),
    OVERSEAS("해외"),
    OTHER("기타");

    private final String name;

    Region(String name) {
        this.name = name;
    }
}
