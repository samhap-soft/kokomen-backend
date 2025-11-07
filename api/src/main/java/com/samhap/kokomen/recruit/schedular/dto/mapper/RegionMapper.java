package com.samhap.kokomen.recruit.schedular.dto.mapper;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.recruit.domain.Region;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum RegionMapper {

    SEOUL("서울", Region.SEOUL),
    GYEONGGI("경기", Region.GYEONGGI),
    INCHEON("인천", Region.INCHEON),
    BUSAN("부산", Region.BUSAN),
    DAEGU("대구", Region.DAEGU),
    GWANGJU("광주", Region.GWANGJU),
    DAEJEON("대전", Region.DAEJEON),
    ULSAN("울산", Region.ULSAN),
    SEJONG("세종", Region.SEJONG),
    GANGWON("강원", Region.GANGWON),
    GYEONGNAM("경남", Region.GYEONGNAM),
    GYEONGBUK("경북", Region.GYEONGBUK),
    JEONNAM("전남", Region.JEONNAM),
    JEONBUK("전북", Region.JEONBUK),
    CHUNGNAM("충남", Region.CHUNGNAM),
    CHUNGBUK("충북", Region.CHUNGBUK),
    JEJU("제주", Region.JEJU),
    OVERSEAS("해외", Region.OVERSEAS),
    OTHER("기타", Region.OTHER);

    private final String name;
    private final Region region;

    RegionMapper(String name, Region region) {
        this.name = name;
        this.region = region;
    }

    public static Region mapRegion(String koreanName) {
        if (koreanName == null || koreanName.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(region -> region.getName().equals(koreanName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown region: " + koreanName))
                .getRegion();
    }
}
