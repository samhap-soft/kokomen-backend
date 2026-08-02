package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import java.util.List;

/**
 * 질문 콜 결과. 빈 목록을 허용하면 질문 0개로 COMPLETED가 되는 경로가 열리므로 생성 시점에 막는다.
 * 원소 타입은 신규 타입을 만들지 않고 기존 GeneratedQuestionDto(question, reason)를 그대로 재사용한다
 * ({@code ResumeAnalysisStateService.completeQuestions}가 이 리스트를 그대로 받는다).
 * 컬럼 한도 절단은 영속화 직전(GeneratedQuestion.forAnalysis)에서 수행한다.
 */
public record ResumeAnalysisQuestionResult(
        List<GeneratedQuestionDto> questions
) {

    public ResumeAnalysisQuestionResult {
        if (questions == null || questions.isEmpty()) {
            throw new ExternalApiException("이력서 분석 질문 생성 결과가 비어 있습니다.");
        }
    }
}
