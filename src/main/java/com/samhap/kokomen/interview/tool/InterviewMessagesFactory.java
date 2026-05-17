package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import java.util.ArrayList;
import java.util.List;

public final class InterviewMessagesFactory {

    private InterviewMessagesFactory() {
    }

    public static List<GptMessage> createGptProceedMessages(QuestionAndAnswers questionAndAnswers) {
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", GptSystemMessageConstant.PROCEED_SYSTEM_MESSAGE));
        addGptMessages(questionAndAnswers, gptMessages);

        return gptMessages;
    }

    public static List<GptMessage> createGptEndMessages(QuestionAndAnswers questionAndAnswers) {
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", GptSystemMessageConstant.END_SYSTEM_MESSAGE));
        addGptMessages(questionAndAnswers, gptMessages);

        return gptMessages;
    }

    private static void addGptMessages(QuestionAndAnswers questionAndAnswers, List<GptMessage> gptMessages) {
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            gptMessages.add(new GptMessage("assistant", questions.get(i).getContent()));
            gptMessages.add(new GptMessage("user", prevAnswers.get(i).getContent()));
        }

        gptMessages.add(new GptMessage("assistant", questionAndAnswers.readCurQuestion().getContent()));
        gptMessages.add(new GptMessage("user", questionAndAnswers.getCurAnswerContent()));
    }
}
