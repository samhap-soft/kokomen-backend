package com.samhap.kokomen.resume.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 신규 extractTextWithLinks가 링크 annotation의 URL을 본문에 노출하는지, 그리고 동결된 extractText의 출력이
 * 전혀 바뀌지 않는지(D2) 검증한다. 픽스처 PDF는 고정 파일이 아니라 PDFBox로 테스트 안에서 생성한다.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();

    @Test
    void 링크_annotation의_URL을_links_블록으로_추출한다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted).isEqualTo("""
                GitHub

                <links>
                https://github.com/example
                </links>""");
    }

    @Test
    void 같은_URL이_여러_번_걸려_있어도_한_번만_출력한다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of(
                "https://github.com/example", "https://github.com/example"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted.split("https://github.com/example", -1)).hasSize(2);
    }

    @Test
    void 여러_URL은_삽입_순서를_유지한다() throws IOException {
        byte[] pdf = pdfWithLinks("Links", List.of(
                "https://github.com/example", "https://example.tistory.com", "https://example.com/paper"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted).contains("""
                <links>
                https://github.com/example
                https://example.tistory.com
                https://example.com/paper
                </links>""");
    }

    @Test
    void 링크가_없으면_links_블록을_붙이지_않는다() throws IOException {
        byte[] pdf = pdfWithLinks("body only document", List.of());

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted).doesNotContain("<links>");
    }

    @Test
    void 링크가_없으면_신규_메서드도_기존_메서드와_완전히_같은_문자열을_반환한다() throws IOException {
        byte[] pdf = pdfWithLinks("Kokomen resume body", List.of());

        assertThat(pdfTextExtractor.extractTextWithLinks(pdf))
                .isEqualTo(pdfTextExtractor.extractText(pdf));
    }

    @Test
    void 기존_extractText는_links_블록도_URL도_출력하지_않는다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));

        String legacy = pdfTextExtractor.extractText(pdf);

        assertThat(legacy).isEqualTo("GitHub");
        assertThat(legacy).doesNotContain("<links>").doesNotContain("https://github.com/example");
    }

    @Test
    void 기존_extractText의_출력은_신규_메서드_본문_구간과_동일하다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));

        String legacy = pdfTextExtractor.extractText(pdf);
        String withLinks = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(withLinks).startsWith(legacy);
        assertThat(withLinks.substring(legacy.length())).isEqualTo("""


                <links>
                https://github.com/example
                </links>""");
    }

    @Test
    void MultipartFile로_받아도_같은_결과를_반환한다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));
        MultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf", pdf);

        assertThat(pdfTextExtractor.extractTextWithLinks(file))
                .isEqualTo(pdfTextExtractor.extractTextWithLinks(pdf));
    }

    @Test
    void 파일이_비어있으면_null을_반환한다() {
        MultipartFile empty = new MockMultipartFile("portfolio", "portfolio.pdf", "application/pdf", new byte[0]);

        assertThat(pdfTextExtractor.extractTextWithLinks(empty)).isNull();
        assertThat(pdfTextExtractor.extractTextWithLinks((MultipartFile) null)).isNull();
        assertThat(pdfTextExtractor.extractTextWithLinks(new byte[0])).isNull();
        assertThat(pdfTextExtractor.extractTextWithLinks((byte[]) null)).isNull();
    }

    @Test
    void 여러_페이지의_링크를_모두_모은다() throws IOException {
        byte[] pdf = twoPagePdfWithLinks(
                List.of("https://github.com/first"), List.of("https://github.com/second"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted)
                .contains("https://github.com/first")
                .contains("https://github.com/second");
    }

    private byte[] pdfWithLinks(String bodyText, List<String> uris) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(page(document, bodyText, uris));
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] twoPagePdfWithLinks(List<String> firstPageUris, List<String> secondPageUris) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(page(document, "first page", firstPageUris));
            document.addPage(page(document, "second page", secondPageUris));
            document.save(out);
            return out.toByteArray();
        }
    }

    // bodyText는 반드시 ASCII여야 한다. Standard14 Helvetica는 WinAnsi 인코딩이라
    // 한글을 showText에 넘기면 IllegalArgumentException(U+XXXX is not available in this font's encoding)이 난다.
    private PDPage page(PDDocument document, String bodyText, List<String> uris) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 700);
            content.showText(bodyText);
            content.endText();
        }

        List<PDAnnotation> annotations = new ArrayList<>();
        for (int i = 0; i < uris.size(); i++) {
            PDActionURI action = new PDActionURI();
            action.setURI(uris.get(i));
            PDAnnotationLink link = new PDAnnotationLink();
            link.setAction(action);
            link.setRectangle(new PDRectangle(72, 650 - (i * 20), 200, 16));
            annotations.add(link);
        }
        page.setAnnotations(annotations);
        return page;
    }
}
