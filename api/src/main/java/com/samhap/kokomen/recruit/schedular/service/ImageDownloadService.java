package com.samhap.kokomen.recruit.schedular.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class ImageDownloadService {

    private final RestClient restClient;

    public ImageDownloadService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    private static final String BASE_URL = "https://d2juy7qzamcf56.cloudfront.net/";
    private static final String IMAGE_DIR = "image";

    public String downloadAndSaveImage(String imagePathOrUrl, String companyId) {
        if (imagePathOrUrl == null || imagePathOrUrl.isBlank()) {
            log.debug("이미지 경로가 null이거나 비어있어 다운로드 스킵");
            return null;
        }

        try {
            boolean isAbsoluteUrl = imagePathOrUrl.startsWith("https://");
            String fullUrl;
            Path targetFile;
            String relativePath;

            if (isAbsoluteUrl) {
                fullUrl = imagePathOrUrl;
                log.debug("절대 URL 이미지 다운로드 시도: {}", fullUrl);

                String extension = extractExtension(imagePathOrUrl);
                String fileName = companyId + extension;

                Path targetDir = Paths.get(IMAGE_DIR, "etc_image");
                targetFile = targetDir.resolve(fileName);
                relativePath = "etc_image/" + fileName;

                if (Files.exists(targetFile)) {
                    log.debug("이미지 파일이 이미 존재함: {}", targetFile);
                    return relativePath;
                }

                Files.createDirectories(targetDir);

            } else {
                fullUrl = BASE_URL + imagePathOrUrl;
                log.debug("상대 경로 이미지 다운로드 시도: {}", fullUrl);

                int lastSlashIndex = imagePathOrUrl.lastIndexOf('/');
                if (lastSlashIndex == -1) {
                    log.warn("잘못된 이미지 경로 형식: {}", imagePathOrUrl);
                    return null;
                }

                String folderName = imagePathOrUrl.substring(0, lastSlashIndex);
                String fileName = imagePathOrUrl.substring(lastSlashIndex + 1);

                Path targetDir = Paths.get(IMAGE_DIR, folderName);
                targetFile = targetDir.resolve(fileName);
                relativePath = folderName + "/" + fileName;

                if (Files.exists(targetFile)) {
                    log.debug("이미지 파일이 이미 존재함: {}", targetFile);
                    return relativePath;
                }

                Files.createDirectories(targetDir);
            }

            byte[] imageBytes = restClient.get()
                    .uri(fullUrl)
                    .retrieve()
                    .body(byte[].class);

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("이미지 다운로드 실패 (빈 데이터): {}", fullUrl);
                return null;
            }

            Files.write(targetFile, imageBytes);
            log.info("이미지 저장 완료: {} ({} bytes)", targetFile, imageBytes.length);

            return relativePath;

        } catch (IOException e) {
            log.error("이미지 저장 중 IO 오류 발생: {} - {}", imagePathOrUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("이미지 다운로드 실패: {} - {}", imagePathOrUrl, e.getMessage());
            return null;
        }
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
}
