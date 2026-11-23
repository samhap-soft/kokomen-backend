package com.samhap.kokomen.interview.service.dto.systemdesign;

/**
 * 요구사항 역질문. 길이는 앞뒤 공백을 지운 뒤 서비스가 검사한다(Bean Validation을 달지 않는다).
 */
public record SystemDesignRequirementQuestionRequest(
        String question
) {
}
