package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class PdfTextExtractor {

    private static final long MEMORY_THRESHOLD = 5L * 1024 * 1024;

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            if (file.getSize() <= MEMORY_THRESHOLD) {
                return extractTextFromMemory(file);
            }
            return extractTextFromStream(file);
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 중 오류 발생", e);
            throw new BadRequestException("PDF 파일에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    private String extractTextFromMemory(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return extractText(document);
        }
    }

    private String extractTextFromStream(MultipartFile file) throws IOException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-", ".pdf");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            try (
                    RandomAccessReadBufferedFile readBuffer = new RandomAccessReadBufferedFile(tempFile);
                    PDDocument document = Loader.loadPDF(readBuffer)
            ) {
                return extractText(document);
            }
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document).trim();
    }
}
