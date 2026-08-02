package com.samhap.kokomen.global.fixture.resume;

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

/**
 * 링크 annotation이 걸린 PDF를 테스트 안에서 만든다. 고정 바이너리를 리소스로 두지 않는 이유는 링크
 * annotation의 유무가 이 픽스처의 전부인데 바이너리로는 그것을 눈으로 확인할 수 없기 때문이다.
 * 링크 추출 자체를 보는 {@code PdfTextExtractorTest}와 제출 경로 두 개의 추출 결과가 같은지 보는
 * {@code ResumeAnalysisFacadeServiceTest}가 같은 픽스처를 쓴다.
 */
public class PdfFixtureBuilder {

    private final List<PageSpec> pages = new ArrayList<>();

    public static PdfFixtureBuilder builder() {
        return new PdfFixtureBuilder();
    }

    // bodyText는 반드시 ASCII여야 한다. Standard14 Helvetica는 WinAnsi 인코딩이라 한글을 showText에 넘기면
    // IllegalArgumentException(U+XXXX is not available in this font's encoding)이 난다.
    public PdfFixtureBuilder page(String bodyText, List<String> uris) {
        pages.add(new PageSpec(bodyText, uris));
        return this;
    }

    public byte[] build() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PageSpec pageSpec : pages) {
                document.addPage(buildPage(document, pageSpec));
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("링크 PDF 픽스처 생성 실패", e);
        }
    }

    private PDPage buildPage(PDDocument document, PageSpec pageSpec) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 700);
            content.showText(pageSpec.bodyText());
            content.endText();
        }
        page.setAnnotations(buildLinkAnnotations(pageSpec.uris()));
        return page;
    }

    private List<PDAnnotation> buildLinkAnnotations(List<String> uris) {
        List<PDAnnotation> annotations = new ArrayList<>();
        for (int i = 0; i < uris.size(); i++) {
            PDActionURI action = new PDActionURI();
            action.setURI(uris.get(i));
            PDAnnotationLink link = new PDAnnotationLink();
            link.setAction(action);
            link.setRectangle(new PDRectangle(72, 650 - (i * 20), 200, 16));
            annotations.add(link);
        }
        return annotations;
    }

    private record PageSpec(String bodyText, List<String> uris) {
    }
}
