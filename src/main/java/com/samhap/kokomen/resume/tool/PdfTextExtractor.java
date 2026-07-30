package com.samhap.kokomen.resume.tool;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
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

    public String extractText(byte[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(pdfData)) {
            return extractText(document);
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 중 오류 발생", e);
            throw new BadRequestException("PDF 파일에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    /**
     * 신규 이력서 분석 경로 전용. PDFTextStripper는 링크 annotation을 추출하지 않아 "GitHub" 같은 글자에 URL이
     * annotation으로만 걸린 이력서에서는 교차 검증 링크가 모델에 보이지 않는다(technical_skills 관찰항목이 구조적으로
     * 채점 불가가 된다). 본문 뒤에 &lt;links&gt; 블록을 덧붙여 해소한다.
     * 기존 extractText 계열과 공유 private extractText(PDDocument)는 절대 수정하지 않는다 — 존치되는
     * ResumeContentService(저장-자료 텍스트 추출 경로)가 지금도 이 메서드를 그대로 호출하므로, 그 메서드를 고치면
     * 하이퍼링크 유무에 따라 신규 플로우와 기존 플로우의 LLM 입력이 서로 달라지게 된다.
     */
    public String extractTextWithLinks(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            if (file.getSize() <= MEMORY_THRESHOLD) {
                return extractTextWithLinksFromMemory(file);
            }
            return extractTextWithLinksFromStream(file);
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 중 오류 발생", e);
            throw new BadRequestException("PDF 파일에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    public String extractTextWithLinks(byte[] pdfData) {
        if (pdfData == null || pdfData.length == 0) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(pdfData)) {
            return extractTextWithLinks(document);
        } catch (IOException e) {
            log.error("PDF 텍스트 추출 중 오류 발생", e);
            throw new BadRequestException("PDF 파일에서 텍스트를 추출하는 데 실패했습니다.");
        }
    }

    private String extractTextWithLinksFromMemory(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return extractTextWithLinks(document);
        }
    }

    private String extractTextWithLinksFromStream(MultipartFile file) throws IOException {
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
                return extractTextWithLinks(document);
            }
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private String extractTextWithLinks(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String body = stripper.getText(document).trim();

        String links = extractLinks(document);
        if (links.isEmpty()) {
            return body;
        }
        return body + "\n\n<links>\n" + links + "\n</links>";
    }

    private String extractLinks(PDDocument document) {
        Set<String> uris = new LinkedHashSet<>();
        for (PDPage page : document.getPages()) {
            try {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (!(annotation instanceof PDAnnotationLink link)) {
                        continue;
                    }
                    if (link.getAction() instanceof PDActionURI uriAction) {
                        String uri = uriAction.getURI();
                        if (uri != null && !uri.isBlank()) {
                            uris.add(uri.trim());
                        }
                    }
                }
            } catch (IOException e) {
                // 링크 부재는 채점 가능한 상태다. 링크 파싱 실패로 분석 전체를 버리지 않고 본문만 사용한다.
                log.warn("PDF 링크 주석 추출 실패 - 본문만 사용합니다.", e);
            }
        }
        return String.join("\n", uris);
    }
}
