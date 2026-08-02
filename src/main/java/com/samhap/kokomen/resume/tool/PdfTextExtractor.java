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

/**
 * 이력서 분석이 LLM에 넣을 PDF 텍스트를 만드는 유일한 지점이다.
 * PDFTextStripper는 링크 annotation을 추출하지 않아 "GitHub" 같은 글자에 URL이 annotation으로만 걸린
 * 이력서에서는 교차 검증 링크가 모델에 보이지 않는다(technical_skills 관찰항목이 구조적으로 채점 불가가 된다).
 * 그래서 본문 뒤에 &lt;links&gt; 블록을 덧붙인다.
 *
 * <p>링크를 붙이지 않는 공개 추출 메서드를 두지 않는다. 제출 경로가 두 개(파일 업로드 →
 * {@code extractTextWithLinks(MultipartFile)}, 저장 자료 재사용 → {@code ResumeContentService} →
 * {@code extractTextWithLinks(byte[])})인데 한쪽만 링크를 보면 같은 이력서가 어느 버튼으로 냈는지에 따라
 * 다른 점수를 받는다. 두 오버로드가 같은 문서에 같은 문자열을 반환한다는 것이 이 클래스의 불변식이며,
 * 링크 없는 오버로드를 되살리면 그 불변식을 코드로 깰 수 있게 된다.
 *
 * <p>두 오버로드의 유일한 차이는 적재 방식이다. {@code MultipartFile}은 5MB를 넘으면 임시 파일로 흘려
 * 힙에 전량을 올리지 않지만, {@code byte[]}는 호출자가 이미 전량을 메모리에 들고 있으므로 분기가 필요 없다.
 */
@Slf4j
@Component
public class PdfTextExtractor {

    private static final long MEMORY_THRESHOLD = 5L * 1024 * 1024;

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
