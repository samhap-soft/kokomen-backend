package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;


public record InterviewProceedStateResponse(
        LlmProceedState llmProceedState,
        InterviewState interviewState,
        AnswerRank curAnswerRank,
        Long nextQuestionId,
        String nextQuestion
) {

    public static InterviewProceedStateResponse createPendingOrFailed(LlmProceedState llmProceedState) {
        return new InterviewProceedStateResponse(llmProceedState, null, null, null, null);
    }

    public static InterviewProceedStateResponse createCompletedAndFinished(Interview interview) {
        return new InterviewProceedStateResponse(LlmProceedState.COMPLETED, InterviewState.FINISHED, null, null, null);
    }

    public static InterviewProceedStateResponse createCompletedAndInProgress(
            Interview interview,
            Answer curAnswer,
            Question nextQuestion
    ) {
        return new InterviewProceedStateResponse(
                LlmProceedState.COMPLETED,
                InterviewState.IN_PROGRESS,
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestion.getContent()
        );
    }
}
