package com.samhap.kokomen.interview.service.dto.check;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.service.dto.QuestionAndAnswerResponse;
import java.util.List;

public record InterviewCheckVoiceModeResponse(
        InterviewState interviewState,
        List<QuestionAndAnswerResponse> prevQuestionsAndAnswers,
        Long curQuestionId,
        String curQuestionVoiceUrl,
        Integer curQuestionCount,
        Integer maxQuestionCount
) implements InterviewCheckResponse {

    public static InterviewCheckResponse of(Interview interview, List<Question> questions, List<Answer> answers, String curQuestionVoiceUrl) {
        if (interview.isInProgress()) {
            return createInProgress(interview, questions, answers, curQuestionVoiceUrl);
        }

        return InterviewCheckResponse.createFinished(interview, questions, answers);
    }

    private static InterviewCheckResponse createInProgress(
            Interview interview,
            List<Question> questions,
            List<Answer> answers,
            String curQuestionVoiceUrl
    ) {
        Question curQuestion = questions.get(questions.size() - 1);
        return new InterviewCheckVoiceModeResponse(
                interview.getInterviewState(),
                InterviewCheckResponse.createPrevQuestionAndAnswers(answers),
                curQuestion.getId(),
                curQuestionVoiceUrl,
                questions.size(),
                interview.getMaxQuestionCount()
        );
    }
}
