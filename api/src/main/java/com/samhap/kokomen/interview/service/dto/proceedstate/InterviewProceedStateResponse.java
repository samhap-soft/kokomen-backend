package com.samhap.kokomen.interview.service.dto.proceedstate;

import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;

public interface InterviewProceedStateResponse {

    InterviewProceedState proceedState();

    static InterviewProceedStateResponse createPendingOrFailed(InterviewProceedState interviewProceedState) {
        return new InterviewProceedStateTextModeResponse(interviewProceedState, null, null, null, null);
    }

    static InterviewProceedStateResponse createCompletedAndFinished() {
        return new InterviewProceedStateTextModeResponse(InterviewProceedState.COMPLETED, InterviewState.FINISHED, null, null, null);
    }
}
