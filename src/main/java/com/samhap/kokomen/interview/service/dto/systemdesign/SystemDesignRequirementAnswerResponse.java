package com.samhap.kokomen.interview.service.dto.systemdesign;

/**
 * 역질문 답. question은 앞뒤 공백을 지운 저장값이고, remainingCount는 이번 질문까지 센 뒤 더 할 수 있는 역질문 수다.
 */
public record SystemDesignRequirementAnswerResponse(
        String question,
        String answer,
        int remainingCount
) {
}
