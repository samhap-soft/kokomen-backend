package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import java.util.ArrayList;
import java.util.List;

public record InterviewHistory(
        List<QuestionAndAnswer> interviewHistory
) {

    public static InterviewHistory from(QuestionAndAnswers questionAndAnswers) {
        List<QuestionAndAnswer> interviewHistory = new ArrayList<>();
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            interviewHistory.add(new QuestionAndAnswer(questions.get(i).getContent(), prevAnswers.get(i).getContent()));
        }
        interviewHistory.add(new QuestionAndAnswer(questionAndAnswers.readCurQuestion().getContent(), questionAndAnswers.getCurAnswerContent()));

        return new InterviewHistory(interviewHistory);
    }
}
