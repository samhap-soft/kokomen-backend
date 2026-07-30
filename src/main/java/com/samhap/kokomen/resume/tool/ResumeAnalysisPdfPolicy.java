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
 * 페이지 수 상한을 둔다. 기존 PdfValidator에 이 검증을 넣으면 동결된 구 평가 업로드 API에 새 거부 조건이
 * 생기므로(D2) 별 클래스로 분리하고 신규 경로에서만 호출한다.
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
