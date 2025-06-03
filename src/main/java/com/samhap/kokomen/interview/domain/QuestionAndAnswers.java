package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

@Getter
public class QuestionAndAnswers {

    private static final int MAX_QUESTION_COUNT = 3;

    private final List<Question> questions;
    private final List<Answer> prevAnswers;
    private final String curAnswerContent;

    public QuestionAndAnswers(List<Question> questions, List<Answer> prevAnswers, String curAnswerContent, Long curQuestionId) {
        questions.sort(Comparator.comparing(Question::getId));
        prevAnswers.sort(Comparator.comparing(Answer::getId));
        validateCurQuestion(questions, curQuestionId);

        this.questions = new ArrayList<>(questions);
        this.prevAnswers = new ArrayList<>(prevAnswers);
        this.curAnswerContent = curAnswerContent;
    }

    private void validateCurQuestion(List<Question> questions, Long curQuestionId) {
        Question curQuestion = questions.get(questions.size() - 1);
        if (!curQuestion.getId().equals(curQuestionId)) {
            throw new IllegalArgumentException("현재 질문이 아닙니다. 현재 질문: " + curQuestion.getContent());
        }
    }

    public Question readCurQuestion() {
        return questions.get(questions.size() - 1);
    }

    public Answer createCurAnswer(GptFeedbackResponse feedback) {
        return new Answer(
                readCurQuestion(),
                curAnswerContent,
                AnswerRank.valueOf(feedback.rank()),
                feedback.feedback()
        );
    }

    public boolean isProceedRequest() {
        return questions.size() < MAX_QUESTION_COUNT;
    }

    public int calculateTotalScore(int curAnswerScore) {
        return prevAnswers.stream()
                .map(answer -> answer.getAnswerRank().getScore())
                .reduce(curAnswerScore, Integer::sum);
    }
}
