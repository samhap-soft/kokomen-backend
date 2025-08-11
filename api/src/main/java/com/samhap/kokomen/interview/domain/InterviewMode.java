package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.constant.AwsConstant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewMode {


    TEXT(1),
    VOICE(2),
    ;

    public static final String ROOT_QUESTION_VOICE_BUCKET_NAME = "root-question-voice/";
    public static final String VOICE_FILE_EXTENSION = ".mp3";
    public static final String ROOT_QUESTION_VOICE_URL_FORMAT =
            AwsConstant.CLOUD_FRONT_DOMAIN_URL + ROOT_QUESTION_VOICE_BUCKET_NAME + "%d" + VOICE_FILE_EXTENSION;

    private final int requiredTokenCount;
}
