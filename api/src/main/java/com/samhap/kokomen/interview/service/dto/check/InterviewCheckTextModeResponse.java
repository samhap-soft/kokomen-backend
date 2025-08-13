package com.samhap.kokomen.interview.service.dto.check;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.service.dto.QuestionAndAnswerResponse;
import java.util.List;

public record InterviewCheckTextModeResponse(
        InterviewState interviewState,
        List<QuestionAndAnswerResponse> prevQuestionsAndAnswers,
        Long curQuestionId,
        String curQuestion,
        Integer curQuestionCount,
        Integer maxQuestionCount
) implements InterviewCheckResponse {

    public static InterviewCheckResponse of(Interview interview, List<Question> questions, List<Answer> answers) {
        if (interview.isInProgress()) {
            return createInProgress(interview, questions, answers);
        }

        return InterviewCheckResponse.createFinished(interview, questions, answers);
    }

    private static InterviewCheckResponse createInProgress(Interview interview, List<Question> questions, List<Answer> answers) {
        Question curQuestion = questions.get(questions.size() - 1);
        return new InterviewCheckTextModeResponse(
                interview.getInterviewState(),
                InterviewCheckResponse.createPrevQuestionAndAnswers(answers),
                curQuestion.getId(),
                curQuestion.getContent(),
                questions.size(),
                interview.getMaxQuestionCount()
        );
    }
}
