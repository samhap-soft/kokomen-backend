package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;


public record InterviewProceedStateTextModeResponse(
        LlmProceedState llmProceedState,
        InterviewState interviewState,
        AnswerRank curAnswerRank,
        Long nextQuestionId,
        String nextQuestion // 음성 모드일 때, nextQuestionVoiceUrl이 되어야 함.
) implements InterviewProceedStateResponse {

    public static InterviewProceedStateTextModeResponse createPendingOrFailed(LlmProceedState llmProceedState) {
        return new InterviewProceedStateTextModeResponse(llmProceedState, null, null, null, null);
    }

    public static InterviewProceedStateTextModeResponse createCompletedAndFinished(Interview interview) {
        return new InterviewProceedStateTextModeResponse(LlmProceedState.COMPLETED, InterviewState.FINISHED, null, null, null);
    }

    public static InterviewProceedStateTextModeResponse createCompletedAndInProgress(
            Interview interview,
            Answer curAnswer,
            Question nextQuestion
    ) {
        return new InterviewProceedStateTextModeResponse(
                LlmProceedState.COMPLETED,
                InterviewState.IN_PROGRESS,
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestion.getContent()
        );
    }
}
