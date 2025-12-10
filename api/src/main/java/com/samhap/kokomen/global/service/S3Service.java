package com.samhap.kokomen.global.service;

import com.samhap.kokomen.global.annotation.ExecutionTimer;
import com.samhap.kokomen.global.constant.AwsConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RequiredArgsConstructor
@ExecutionTimer
@Service
public class S3Service {

    private static final String S3_BUCKET_NAME = "kokomen-new";

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

    public boolean exists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    public byte[] downloadFile(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(S3_BUCKET_NAME)
                .key(key)
                .build();

        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    public byte[] downloadFileFromUrl(String cdnUrl) {
        String key = extractKeyFromCdnUrl(cdnUrl);
        return downloadFile(key);
    }

    private String extractKeyFromCdnUrl(String cdnUrl) {
        if (cdnUrl.startsWith(AwsConstant.CLOUD_FRONT_DOMAIN_URL)) {
            return cdnUrl.substring(AwsConstant.CLOUD_FRONT_DOMAIN_URL.length());
        }
        throw new IllegalArgumentException("Invalid CDN URL: " + cdnUrl);
    }
}
