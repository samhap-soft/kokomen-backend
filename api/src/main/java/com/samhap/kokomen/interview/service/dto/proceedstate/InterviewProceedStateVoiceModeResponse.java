package com.samhap.kokomen.interview.service.dto.proceedstate;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.LlmProceedState;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewProceedStateVoiceModeResponse(
        LlmProceedState llmProceedState,
        InterviewState interviewState,
        AnswerRank curAnswerRank,
        Long nextQuestionId,
        String nextQuestionVoiceUrl
) implements InterviewProceedStateResponse {

    public static InterviewProceedStateResponse createCompletedAndInProgress(
            Answer curAnswer,
            Question nextQuestion,
            String nextQuestionVoiceUrl
    ) {
        return new InterviewProceedStateVoiceModeResponse(
                LlmProceedState.COMPLETED,
                InterviewState.IN_PROGRESS,
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestionVoiceUrl
        );
    }
}
