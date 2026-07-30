package com.samhap.kokomen.resume.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ResumeAnalysisPdfPolicyTest {

    private final ResumeAnalysisPdfPolicy resumeAnalysisPdfPolicy = new ResumeAnalysisPdfPolicy();

    @Test
    void 페이지_수가_상한_이하면_통과한다() throws IOException {
        MultipartFile file = pdfFile(ResumeAnalysisPdfPolicy.MAX_PAGE_COUNT);

        assertThatCode(() -> resumeAnalysisPdfPolicy.validatePageCount(file)).doesNotThrowAnyException();
    }

    @Test
    void 페이지_수가_상한을_넘으면_예외가_발생한다() throws IOException {
        MultipartFile file = pdfFile(ResumeAnalysisPdfPolicy.MAX_PAGE_COUNT + 1);

        assertThatThrownBy(() -> resumeAnalysisPdfPolicy.validatePageCount(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("PDF는 " + ResumeAnalysisPdfPolicy.MAX_PAGE_COUNT + "페이지를 초과할 수 없습니다.");
    }

    @Test
    void 파일이_없으면_페이지_검증을_건너뛴다() {
        assertThatCode(() -> resumeAnalysisPdfPolicy.validatePageCount(null)).doesNotThrowAnyException();
        assertThatCode(() -> resumeAnalysisPdfPolicy.validatePageCount(
                new MockMultipartFile("portfolio", "portfolio.pdf", "application/pdf", new byte[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void PDF가_아닌_바이트가_오면_읽을_수_없다는_예외가_발생한다() {
        MultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf",
                "이것은 PDF가 아니다".getBytes());

        assertThatThrownBy(() -> resumeAnalysisPdfPolicy.validatePageCount(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("PDF 파일을 읽을 수 없습니다.");
    }

    @Test
    void 페이지_상한은_100이다() {
        assertThat(ResumeAnalysisPdfPolicy.MAX_PAGE_COUNT).isEqualTo(100);
    }

    private MultipartFile pdfFile(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(out);
            return new MockMultipartFile("resume", "resume.pdf", "application/pdf", out.toByteArray());
        }
    }
}
