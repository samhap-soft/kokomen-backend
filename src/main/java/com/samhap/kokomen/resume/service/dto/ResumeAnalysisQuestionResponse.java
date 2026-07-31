package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;

public record ResumeAnalysisQuestionResponse(
        Long generatedQuestionId,
        Integer questionOrder,
        String question,
        String reason
) {

    public static ResumeAnalysisQuestionResponse from(GeneratedQuestion question) {
        return new ResumeAnalysisQuestionResponse(question.getId(), question.getQuestionOrder(),
                question.getContent(), question.getReason());
    }
}
