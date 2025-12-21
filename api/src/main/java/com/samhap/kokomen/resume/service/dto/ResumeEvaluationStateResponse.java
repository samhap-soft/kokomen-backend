package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.domain.ResumeEvaluationState;

public record ResumeEvaluationStateResponse(
        ResumeEvaluationState state,
        ResumeEvaluationResponse result
) {
    public static ResumeEvaluationStateResponse pending() {
        return new ResumeEvaluationStateResponse(ResumeEvaluationState.PENDING, null);
    }

    public static ResumeEvaluationStateResponse failed() {
        return new ResumeEvaluationStateResponse(ResumeEvaluationState.FAILED, null);
    }

    public static ResumeEvaluationStateResponse completed(ResumeEvaluationResponse result) {
        return new ResumeEvaluationStateResponse(ResumeEvaluationState.COMPLETED, result);
    }

    public static ResumeEvaluationStateResponse from(ResumeEvaluation evaluation) {
        if (evaluation.isPending()) {
            return pending();
        }
        if (evaluation.isCompleted()) {
            return completed(ResumeEvaluationResponse.from(evaluation));
        }
        return failed();
    }
}
