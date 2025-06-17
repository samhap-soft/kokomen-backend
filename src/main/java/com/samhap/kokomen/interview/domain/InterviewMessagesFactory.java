package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.external.dto.request.Message;
import java.util.ArrayList;
import java.util.List;

public final class InterviewMessagesFactory {

    private InterviewMessagesFactory() {
    }

    public static List<Message> createProceedMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", GptSystemMessageConstant.PROCEED_SYSTEM_MESSAGE));
        addMessages(questionAndAnswers, messages);

        return messages;
    }

    public static List<Message> createEndMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", GptSystemMessageConstant.END_SYSTEM_MESSAGE));
        addMessages(questionAndAnswers, messages);

        return messages;
    }

    private static void addMessages(QuestionAndAnswers questionAndAnswers, List<Message> messages) {
        questionAndAnswers.getPrevAnswers().forEach(answer -> {
            messages.add(new Message("assistant", answer.getQuestion().getContent()));
            messages.add(new Message("user", answer.getContent()));
        });

        messages.add(new Message("assistant", questionAndAnswers.readCurQuestion().getContent()));
        messages.add(new Message("user", questionAndAnswers.getCurAnswerContent()));
    }
}
