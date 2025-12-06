package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.resume.domain.ResumeEvaluationState;

public record NonMemberResumeEvaluationData(
        ResumeEvaluationState state,
        ResumeEvaluationRequest request,
        ResumeEvaluationResponse result
) {
    public static NonMemberResumeEvaluationData pending(ResumeEvaluationRequest request) {
        return new NonMemberResumeEvaluationData(ResumeEvaluationState.PENDING, request, null);
    }

    public static NonMemberResumeEvaluationData completed(ResumeEvaluationRequest request,
                                                          ResumeEvaluationResponse result) {
        return new NonMemberResumeEvaluationData(ResumeEvaluationState.COMPLETED, request, result);
    }

    public static NonMemberResumeEvaluationData failed(ResumeEvaluationRequest request) {
        return new NonMemberResumeEvaluationData(ResumeEvaluationState.FAILED, request, null);
    }
}
