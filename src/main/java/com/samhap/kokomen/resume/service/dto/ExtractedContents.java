package com.samhap.kokomen.resume.service.dto;

/**
 * 요청 스레드에서 추출을 끝낸 원문 텍스트. MultipartFile·byte[]는 워커로 넘기지 않는다(§6-2).
 */
public record ExtractedContents(
        String resumeText,
        String portfolioText
) {
}
