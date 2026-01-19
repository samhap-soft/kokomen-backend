package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.constant.AwsConstant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class CareerMaterialsPathResolver {

    private static final String PDF_FILE_EXTENSION = ".pdf";
    private static final String FOLDER_DELIMITER = "/";

    private final String resumeS3Path;
    private final String portfolioS3Path;

    public CareerMaterialsPathResolver(
            @Value("${aws.resume-s3-path}") String resumeS3Path,
            @Value("${aws.portfolio-s3-path}") String portfolioS3Path) {
        this.resumeS3Path = resumeS3Path;
        this.portfolioS3Path = portfolioS3Path;
    }

    public String resolveResumeCdnPath(String s3Key) {
        return AwsConstant.CLOUD_FRONT_DOMAIN_URL + s3Key;
    }

    public String resolveResumeS3Key(Long memberId, String title) {
        return resumeS3Path + memberId + FOLDER_DELIMITER + title + "-" + UUID.randomUUID() + PDF_FILE_EXTENSION;
    }

    public String resolvePortfolioCdnPath(String s3Key) {
        return AwsConstant.CLOUD_FRONT_DOMAIN_URL + s3Key;
    }

    public String resolvePortfolioS3Key(Long memberId, String title) {
        return portfolioS3Path + memberId + FOLDER_DELIMITER + title + "-" + UUID.randomUUID() + PDF_FILE_EXTENSION;
    }
}
