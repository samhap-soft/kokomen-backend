package com.samhap.kokomen.resume.service.dto;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public record ResumeFileData(
        byte[] content,
        String filename
) {

    public static ResumeFileData from(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return new ResumeFileData(file.getBytes(), file.getOriginalFilename());
    }

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
