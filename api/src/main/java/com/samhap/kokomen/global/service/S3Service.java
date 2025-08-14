package com.samhap.kokomen.global.service;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
@ExecutionTimer
@Service
public class S3Service {

    private static final String S3_BUCKET_NAME = "kokomen";

    private final S3Client s3Client;

    public void uploadS3File(String key, byte[] data, String contentType) {
        PutObjectRequest s3Request = PutObjectRequest.builder()
                .bucket(S3_BUCKET_NAME)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        s3Client.putObject(s3Request, RequestBody.fromBytes(data));
    }
}
