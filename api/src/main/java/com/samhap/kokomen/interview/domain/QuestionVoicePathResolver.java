package com.samhap.kokomen.interview.domain;

import static com.samhap.kokomen.global.constant.AwsConstant.CLOUD_FRONT_DOMAIN_URL;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// S3에서 dev, prod 경로가 나뉘면서 application.yml에서 값을 주입받기 위해 빈으로 등록 필수
@Getter
@Component
public class QuestionVoicePathResolver {

    private static final String AUDIO_FILE_EXTENSION = ".wav";

    private final String rootQuestionS3Path;
    private final String nextQuestionS3Path;

    public QuestionVoicePathResolver(
            @Value("${aws.root-question-s3-path}") String rootQuestionS3Path,
            @Value("${aws.next-question-s3-path}") String nextQuestionS3Path
    ) {
        this.rootQuestionS3Path = rootQuestionS3Path;
        this.nextQuestionS3Path = nextQuestionS3Path;
    }

    public String resolveRootQuestionCdnPath(Long rootQuestionId) {
        return CLOUD_FRONT_DOMAIN_URL + rootQuestionS3Path + rootQuestionId + AUDIO_FILE_EXTENSION;
    }

    public String resolveNextQuestionCdnPath(Long nextQuestionId) {
        return CLOUD_FRONT_DOMAIN_URL + nextQuestionS3Path + nextQuestionId + AUDIO_FILE_EXTENSION;
    }

    public String resolveNextQuestionS3Key(Long nextQuestionId) {
        return nextQuestionS3Path + nextQuestionId + AUDIO_FILE_EXTENSION;
    }
}
