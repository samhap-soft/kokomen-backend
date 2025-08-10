package com.samhap.kokomen.interview.service.dto.check;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.service.dto.QuestionAndAnswerResponse;
import java.util.List;

public interface InterviewCheckResponse {

    static InterviewCheckResponse createFinished(Interview interview, List<Question> questions, List<Answer> answers) {
        return new InterviewCheckTextModeResponse(
                interview.getInterviewState(),
                createPrevQuestionAndAnswers(answers),
                null,
                null,
                questions.size(),
                interview.getMaxQuestionCount()
        );
    }

    static List<QuestionAndAnswerResponse> createPrevQuestionAndAnswers(List<Answer> answers) {
        return answers.stream()
                .map(QuestionAndAnswerResponse::new)
                .toList();
    }
}
