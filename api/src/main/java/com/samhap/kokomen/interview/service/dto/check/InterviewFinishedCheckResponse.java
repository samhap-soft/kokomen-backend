package com.samhap.kokomen.interview.service.dto.check;

import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.service.dto.QuestionAndAnswerResponse;
import java.util.List;

public record InterviewFinishedCheckResponse(
        InterviewState interviewState,
        List<QuestionAndAnswerResponse> prevQuestionsAndAnswers,
        Integer curQuestionCount,
        Integer maxQuestionCount
) implements InterviewCheckResponse {
}
