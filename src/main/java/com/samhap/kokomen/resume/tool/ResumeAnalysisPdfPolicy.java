package com.samhap.kokomen.resume.tool;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 신규 이력서 분석 경로 전용 PDF 정책. 파일 크기만으로는 파싱 비용을 제한할 수 없어(수천 페이지 PDF)
 * 페이지 수 상한을 둔다. PdfValidator는 현재 프로덕션 호출자가 없지만, 그 기존 동작을 이 검증 때문에 바꾸지
 * 않기 위해 별 클래스로 분리했다 — 신규 업로드 경로가 추가되면 PdfValidator와 이 정책 양쪽을 함께 호출하게
 * 되므로, 두 클래스의 책임을 지금부터 섞지 않는다.
 */
@Slf4j
@Component
public class ResumeAnalysisPdfPolicy {

    public static final int MAX_PAGE_COUNT = 100;

    public void validatePageCount(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        try (
                RandomAccessRead read = new RandomAccessReadBuffer(file.getInputStream());
                PDDocument document = Loader.loadPDF(read)
        ) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PAGE_COUNT) {
                throw new BadRequestException("PDF는 " + MAX_PAGE_COUNT + "페이지를 초과할 수 없습니다.");
            }
        } catch (IOException e) {
            log.warn("PDF 페이지 수 확인 실패 - fileName: {}", file.getOriginalFilename(), e);
            throw new BadRequestException("PDF 파일을 읽을 수 없습니다.");
        }
    }
}
