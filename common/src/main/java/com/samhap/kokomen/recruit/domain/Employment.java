package com.samhap.kokomen.recruit.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Getter
public enum Employment {

    SERVER_BACKEND("서버·백엔드"),
    FRONTEND("프론트엔드"),
    WEB_FULLSTACK("웹풀스택"),
    ANDROID("안드로이드"),
    IOS("iOS"),
    CROSS_PLATFORM("크로스 플랫폼"),
    DBA("DBA"),
    DEVOPS_SRE("DevOps·SRE"),
    SYSTEM_NETWORK("시스템·네트워크"),
    SYSTEM_SOFTWARE("시스템 소프트웨어"),
    SOFTWARE_ENGINEER("소프트웨어 엔지니어"),
    SECURITY("정보보호·보안"),
    EMBEDDED_SOFTWARE("임베디드 소프트웨어"),
    ROBOT_SW("로봇SW"),
    QA_TEST("QA·테스트"),
    IOT("사물인터넷(IoT)"),
    APPLICATION("응용 프로그램"),
    BLOCKCHAIN("블록체인"),
    DEV_PM("개발PM"),
    WEB_PUBLISHING("웹 퍼블리싱"),
    VR_AR_3D("VR·AR·3D"),
    ERP_SAP("ERP·SAP"),
    GRAPHICS("그래픽스"),
    HARDWARE_ENGINEER("하드웨어엔지니어"),
    OTHER_IT_DEV("기타IT·개발");

    private final String name;

    Employment(String name) {
        this.name = name;
    }

    public static Employment findByName(String name) {
        return Arrays.stream(values())
                .filter(employment -> employment.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No enum constant with name: " + name));
    }

    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(Employment::getName)
                .toList();
    }
}
