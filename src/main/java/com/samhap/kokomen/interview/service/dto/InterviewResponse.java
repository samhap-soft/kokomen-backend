package com.samhap.kokomen.interview.service.dto;

import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import java.util.List;

public record InterviewResponse(
        InterviewState interviewState,
        List<QuestionAndAnswerResponse> prevQuestionsAndAnswers,
        Long curQuestionId,
        String curQuestion,
        Integer curQuestionCount,
        Integer maxQuestionCount
) {

    public static InterviewResponse createInProgress(Interview interview, List<Question> questions, List<Answer> answers) {
        Question curQuestion = questions.get(questions.size() - 1);
        return new InterviewResponse(
                interview.getInterviewState(),
                createPrevQuestionAndAnswers(answers),
                curQuestion.getId(),
                curQuestion.getContent(),
                questions.size(),
                interview.getMaxQuestionCount()
        );
    }

    public static InterviewResponse createFinished(Interview interview, List<Question> questions, List<Answer> answers) {
        return new InterviewResponse(
                interview.getInterviewState(),
                createPrevQuestionAndAnswers(answers),
                null,
                null,
                questions.size(),
                interview.getMaxQuestionCount()
        );
    }

    private static List<QuestionAndAnswerResponse> createPrevQuestionAndAnswers(List<Answer> answers) {
        return answers.stream()
                .map(QuestionAndAnswerResponse::new)
                .toList();
    }
}
