package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

@Getter
public class QuestionAndAnswers {

    private final List<Question> questions;
    private final List<Answer> prevAnswers;
    private final String curAnswerContent;
    private final Interview interview;

    // TODO: 생성자에서 예외가 발생하더라도 객체는 힙에 남아있는지 실험해보기
    public QuestionAndAnswers(List<Question> questions, List<Answer> prevAnswers, String curAnswerContent, Long curQuestionId, Interview interview) {
        this.questions = questions.stream()
                .sorted(Comparator.comparing(Question::getId))
                .toList();
        this.prevAnswers = prevAnswers.stream()
                .sorted(Comparator.comparing(Answer::getId))
                .toList();
        this.curAnswerContent = curAnswerContent;
        this.interview = interview;

        validateInterviewProceed();
        validateQuestionsAndAnswersSize();
        validateCurQuestion(curQuestionId);
    }

    private void validateInterviewProceed() {
        if (prevAnswers.size() == interview.getMaxQuestionCount()) {
            throw new BadRequestException("인터뷰가 종료되었습니다. 더 이상 답변 받을 수 없습니다.");
        }
    }

    private void validateQuestionsAndAnswersSize() {
        if (questions.size() != prevAnswers.size() + 1) {
            throw new IllegalArgumentException("질문과 답변의 개수가 일치하지 않습니다.");
        }
    }

    private void validateCurQuestion(Long curQuestionId) {
        Question curQuestion = readCurQuestion();
        if (!curQuestion.getId().equals(curQuestionId)) {
            throw new BadRequestException("현재 질문이 아닙니다. 현재 질문 id: " + curQuestion.getId());
        }
    }

    public Answer createCurAnswer(GptFeedbackResponse feedback) {
        return new Answer(
                readCurQuestion(),
                curAnswerContent,
                AnswerRank.valueOf(feedback.rank()),
                feedback.feedback()
        );
    }

    public Question readCurQuestion() {
        return questions.get(questions.size() - 1);
    }

    public boolean isProceedRequest() {
        return questions.size() < interview.getMaxQuestionCount();
    }

    public int calculateTotalScore(int curAnswerScore) {
        return prevAnswers.stream()
                .map(answer -> answer.getAnswerRank().getScore())
                .reduce(curAnswerScore, Integer::sum);
    }
}
