package com.samhap.kokomen.recruit.schedular.service;

import com.samhap.kokomen.global.constant.AwsConstant;
import com.samhap.kokomen.global.service.S3Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class ImageDownloadService {

    private static final String S3_BASE_PATH = "recruit/company/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String FOLDER_DELIMITER = "/";

    private final RestClient restClient;
    private final S3Service s3Service;

    public ImageDownloadService(RestClient.Builder builder, S3Service s3Service) {
        this.restClient = builder.build();
        this.s3Service = s3Service;
    }

    public String downloadAndSaveImage(String imagePathOrUrl, String companyId) {
        if (imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            return null;
        }

        try {
            String fullUrl = getFullUrl(imagePathOrUrl);

            log.debug("이미지 다운로드 시도: {}", fullUrl);

            String dateFolder = LocalDate.now().format(DATE_FORMATTER);
            String extension = extractExtension(fullUrl);
            String fileName = companyId + extension;
            String s3Key = S3_BASE_PATH + dateFolder + FOLDER_DELIMITER + fileName;
            String relativePath = dateFolder + FOLDER_DELIMITER + fileName;

            if (s3Service.exists(s3Key)) {
                log.debug("이미지 파일이 S3에 이미 존재함: {}", s3Key);
                return relativePath;
            }

            byte[] imageBytes = restClient.get()
                    .uri(fullUrl)
                    .retrieve()
                    .body(byte[].class);

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("이미지 다운로드 실패 (빈 데이터): {}", fullUrl);
                return null;
            }

            String contentType = determineContentType(extension);
            s3Service.uploadS3File(s3Key, imageBytes, contentType);

            return relativePath;

        } catch (Exception e) {
            log.error("이미지 다운로드 및 업로드 실패: {} - {}", imagePathOrUrl, e.getMessage());
            return null;
        }
    }

    private String getFullUrl(String imagePathOrUrl) {
        if (imagePathOrUrl.startsWith("https://")) {
            return imagePathOrUrl;
        }
        return AwsConstant.CLOUD_FRONT_DOMAIN_URL + imagePathOrUrl;
    }

    private String extractExtension(String url) {
        int queryIndex = url.indexOf('?');
        String urlWithoutQuery = queryIndex > 0 ? url.substring(0, queryIndex) : url;

        int lastDotIndex = urlWithoutQuery.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < urlWithoutQuery.length() - 1) {
            return urlWithoutQuery.substring(lastDotIndex);
        }

        return ".jpg";
    }

    private String determineContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}

