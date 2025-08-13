package com.samhap.kokomen.interview.service.dto.proceedstate;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
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

    public static InterviewProceedStateTextModeResponse createCompletedAndInProgress(Answer curAnswer, Question nextQuestion) {
        return new InterviewProceedStateTextModeResponse(
                LlmProceedState.COMPLETED,
                InterviewState.IN_PROGRESS,
                curAnswer.getAnswerRank(),
                nextQuestion.getId(),
                nextQuestion.getContent()
        );
    }
}
