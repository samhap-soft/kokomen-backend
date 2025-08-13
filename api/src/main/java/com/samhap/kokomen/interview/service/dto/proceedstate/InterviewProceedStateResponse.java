package com.samhap.kokomen.interview.service.dto.proceedstate;

import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;

public interface InterviewProceedStateResponse {

    LlmProceedState llmProceedState();

    static InterviewProceedStateResponse createPendingOrFailed(LlmProceedState llmProceedState) {
        return new InterviewProceedStateTextModeResponse(llmProceedState, null, null, null, null);
    }

    static InterviewProceedStateResponse createCompletedAndFinished() {
        return new InterviewProceedStateTextModeResponse(LlmProceedState.COMPLETED, InterviewState.FINISHED, null, null, null);
    }
}
