package com.samhap.kokomen.interview.service.dto.systemdesign;

/**
 * 리포트의 평가 포인트 하나. isMet은 마지막 답변 채점이 판정한 충족 여부다(JSON 키 is_met).
 */
public record SystemDesignEvaluationPointResponse(
        String description,
        Boolean isMet
) {
}
