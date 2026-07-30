package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import java.util.List;

/**
 * 이력서 분석 질문 tool 응답의 와이어 DTO. Bedrock(tool-use)과 GPT(function-calling)가 같은 형상을 쓴다.
 * 원소 타입은 기존 GeneratedQuestionDto(question, reason)를 재사용한다(신규 항목 타입을 만들지 않는다).
 */
public record ResumeAnalysisQuestionsFlatResponse(
        List<GeneratedQuestionDto> questions
) {

    public ResumeAnalysisQuestionResult toResult() {
        return new ResumeAnalysisQuestionResult(questions);
    }
}
