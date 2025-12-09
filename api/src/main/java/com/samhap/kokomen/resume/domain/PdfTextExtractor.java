package com.samhap.kokomen.resume.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PdfTextExtractor {

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text.trim();
        } catch (IOException e) {
            throw new BadRequestException("PDF 파일에서 텍스트를 추출할 수 없습니다.");
        }
    }
}
