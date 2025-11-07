package com.samhap.kokomen.recruit.schedular.dto.mapper;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.recruit.domain.Employment;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum EmploymentMapper {

    SERVER_BACKEND("서버_백엔드", Employment.SERVER_BACKEND),
    FRONTEND("프론트엔드", Employment.FRONTEND),
    WEB_FULLSTACK("웹풀스택", Employment.WEB_FULLSTACK),
    ANDROID("안드로이드", Employment.ANDROID),
    IOS("iOS", Employment.IOS),
    CROSS_PLATFORM("크로스플랫폼", Employment.CROSS_PLATFORM),
    DBA("DBA", Employment.DBA),
    DEVOPS_SRE("DevOps_SRE", Employment.DEVOPS_SRE),
    SYSTEM_NETWORK("시스템_네트워크", Employment.SYSTEM_NETWORK),
    SYSTEM_SOFTWARE("시스템소프트웨어", Employment.SYSTEM_SOFTWARE),
    SOFTWARE_ENGINEER("소프트웨어엔지니어", Employment.SOFTWARE_ENGINEER),
    SECURITY("정보보호_보안", Employment.SECURITY),
    EMBEDDED_SOFTWARE("임베디드소프트웨어", Employment.EMBEDDED_SOFTWARE),
    ROBOT_SW("로봇SW", Employment.ROBOT_SW),
    QA_TEST("QA_테스트", Employment.QA_TEST),
    IOT("사물인터넷_IoT", Employment.IOT),
    APPLICATION("응용프로그램", Employment.APPLICATION),
    BLOCKCHAIN("블록체인", Employment.BLOCKCHAIN),
    DEV_PM("개발PM", Employment.DEV_PM),
    WEB_PUBLISHING("웹퍼블리싱", Employment.WEB_PUBLISHING),
    VR_AR_3D("VR_AR_3D", Employment.VR_AR_3D),
    ERP_SAP("ERP_SAP", Employment.ERP_SAP),
    GRAPHICS("그래픽스", Employment.GRAPHICS),
    HARDWARE_ENGINEER("하드웨어엔지니어", Employment.HARDWARE_ENGINEER),
    OTHER_IT_DEV("기타IT_개발", Employment.OTHER_IT_DEV);

    private final String name;
    private final Employment employment;

    EmploymentMapper(String name, Employment employment) {
        this.name = name;
        this.employment = employment;
    }

    public static Employment mapEmployment(String koreanName) {
        if (koreanName == null || koreanName.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(employment -> employment.getName().equals(koreanName))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown employment: " + koreanName))
                .getEmployment();
    }
}
