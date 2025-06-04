package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

@Getter
public class QuestionAndAnswers {

    private static final int MAX_QUESTION_COUNT = 3;

    private final List<Question> questions;
    private final List<Answer> prevAnswers;
    private final String curAnswerContent;

    // TODO: 생성자에서 예외가 발생하더라도 객체는 힙에 남아있는지 실험해보기
    public QuestionAndAnswers(List<Question> questions, List<Answer> prevAnswers, String curAnswerContent, Long curQuestionId) {
        this.questions = questions.stream()
                .sorted(Comparator.comparing(Question::getId))
                .toList();
        this.prevAnswers = prevAnswers.stream()
                .sorted(Comparator.comparing(Answer::getId))
                .toList();
        this.curAnswerContent = curAnswerContent;

        validateCurQuestion(curQuestionId);
    }

    private void validateCurQuestion(Long curQuestionId) {
        Question curQuestion = readCurQuestion();
        if (!curQuestion.getId().equals(curQuestionId)) {
            throw new IllegalArgumentException("현재 질문이 아닙니다. 현재 질문 id: " + curQuestion.getId());
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
