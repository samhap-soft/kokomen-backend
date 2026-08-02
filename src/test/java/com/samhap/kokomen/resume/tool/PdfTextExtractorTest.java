package com.samhap.kokomen.resume.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.fixture.resume.PdfFixtureBuilder;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * extractTextWithLinks가 링크 annotation의 URL을 본문 뒤에 노출하는지, 그리고 두 오버로드가 같은 PDF에
 * 같은 문자열을 반환하는지 검증한다. 픽스처 PDF는 고정 파일이 아니라 PDFBox로 테스트 안에서 생성한다.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor pdfTextExtractor = new PdfTextExtractor();

    @Test
    void 링크_annotation의_URL을_links_블록으로_추출한다() {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted).isEqualTo("""
                GitHub

                <links>
                https://github.com/example
                </links>""");
    }

    @Test
    void 같은_URL이_여러_번_걸려_있어도_한_번만_출력한다() {
        byte[] pdf = pdfWithLinks("GitHub", List.of(
                "https://github.com/example", "https://github.com/example"));

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted.split("https://github.com/example", -1)).hasSize(2);
    }

    @Test
    void 여러_URL은_삽입_순서를_유지한다() {
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
    void 링크가_없으면_links_블록을_붙이지_않고_본문만_반환한다() {
        byte[] pdf = pdfWithLinks("body only document", List.of());

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted).isEqualTo("body only document");
    }

    @Test
    void PDFTextStripper만으로는_링크_URL이_보이지_않고_links_블록은_그_본문_뒤에만_덧붙는다() throws IOException {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));

        String stripperOnly = stripperText(pdf);
        String withLinks = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(stripperOnly).isEqualTo("GitHub");
        assertThat(withLinks).startsWith(stripperOnly);
        assertThat(withLinks.substring(stripperOnly.length())).isEqualTo("""


                <links>
                https://github.com/example
                </links>""");
    }

    @Test
    void MultipartFile로_받아도_같은_결과를_반환한다() {
        byte[] pdf = pdfWithLinks("GitHub", List.of("https://github.com/example"));
        MultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf", pdf);

        assertThat(pdfTextExtractor.extractTextWithLinks(file)).isEqualTo("""
                GitHub

                <links>
                https://github.com/example
                </links>""");
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
    void 여러_페이지의_링크를_모두_모은다() {
        byte[] pdf = PdfFixtureBuilder.builder()
                .page("first page", List.of("https://github.com/first"))
                .page("second page", List.of("https://github.com/second"))
                .build();

        String extracted = pdfTextExtractor.extractTextWithLinks(pdf);

        assertThat(extracted)
                .contains("https://github.com/first")
                .contains("https://github.com/second");
    }

    private byte[] pdfWithLinks(String bodyText, List<String> uris) {
        return PdfFixtureBuilder.builder()
                .page(bodyText, uris)
                .build();
    }

    // 프로덕션이 링크 블록을 덧붙이는 이유가 라이브러리의 한계라는 것을 픽스처로 직접 확인한다.
    private String stripperText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document).trim();
        }
    }
}
