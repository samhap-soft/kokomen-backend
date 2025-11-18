package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PdfValidator {

    private static final int MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String ALLOWED_CONTENT_TYPE = "application/pdf";
    private static final String ALLOWED_EXTENSION = ".pdf";

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("파일이 비어있습니다.");
        }

        validateFileSize(file);
        validateContentType(file);
        validateFileExtension(file);
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("파일 크기는 10MB를 초과할 수 없습니다.");
        }
    }

    private void validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPE.equals(contentType)) {
            throw new BadRequestException("파일은 PDF 형식만 업로드 가능합니다.");
        }
    }

    private void validateFileExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(ALLOWED_EXTENSION)) {
            throw new BadRequestException("파일은 PDF 형식(.pdf)만 업로드 가능합니다.");
        }
    }
}
