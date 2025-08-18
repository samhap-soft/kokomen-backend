package com.samhap.kokomen.interview.service.dto.proceedstate;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewProceedState;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;

public record InterviewProceedStateVoiceModeResponse(
        InterviewProceedState proceedState,
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
                InterviewProceedState.COMPLETED,
                InterviewState.IN_PROGRESS,
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestionVoiceUrl
        );
    }

    @Override
    public InterviewProceedState proceedState() {
        return proceedState;
    }
}
