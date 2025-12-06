package com.samhap.kokomen.resume.service.dto;

public record ResumeEvaluationSubmitResponse(
        String evaluationId
) {
    public static ResumeEvaluationSubmitResponse from(Long id) {
        return new ResumeEvaluationSubmitResponse(String.valueOf(id));
    }

    public static ResumeEvaluationSubmitResponse fromUuid(String uuid) {
        return new ResumeEvaluationSubmitResponse("uuid-" + uuid);
    }
}
