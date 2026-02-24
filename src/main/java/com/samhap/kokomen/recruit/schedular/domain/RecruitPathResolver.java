package com.samhap.kokomen.recruit.schedular.domain;

import com.samhap.kokomen.global.constant.AwsConstant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecruitPathResolver {

    private static final String FOLDER_DELIMITER = "/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final String companyS3Path;

    public RecruitPathResolver(
            @Value("${aws.company-s3-path}") String companyS3Path) {
        this.companyS3Path = companyS3Path;
    }

    public String resolveCompanyCdnPath(String s3Key) {
        return AwsConstant.CLOUD_FRONT_DOMAIN_URL + s3Key;
    }

    public String resolveCompanyS3Key(String fileName) {
        String dateFolder = LocalDate.now().format(DATE_FORMATTER);
        return companyS3Path + dateFolder + FOLDER_DELIMITER + fileName;
    }
}
