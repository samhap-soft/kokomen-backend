package com.samhap.kokomen.interview.domain;

import static com.samhap.kokomen.global.constant.AwsConstant.CLOUD_FRONT_DOMAIN_URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// S3에서 dev, prod 경로가 나뉘면서 application.yml에서 값을 주입받기 위해 빈으로 등록 필수
@Component
public class RootQuestionVoicePathResolver {

    private final String rootQuestionS3Path;

    public RootQuestionVoicePathResolver(@Value("${aws.root-question-s3-path}") String rootQuestionS3Path) {
        this.rootQuestionS3Path = rootQuestionS3Path;
    }

    public String resolvePath(Long rootQuestionId) {
        return CLOUD_FRONT_DOMAIN_URL + rootQuestionS3Path + rootQuestionId + ".mp3";
    }
}
