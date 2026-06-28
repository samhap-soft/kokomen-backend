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
        String systemMessage = switch (questionAndAnswers.getInterview().getInterviewType()) {
            case LIVE_CODING -> GptSystemMessageConstant.CODING_PROCEED_SYSTEM_MESSAGE;
            case PERSONALITY -> GptSystemMessageConstant.PERSONALITY_PROCEED_SYSTEM_MESSAGE;
            case CATEGORY_BASED, RESUME_BASED -> GptSystemMessageConstant.PROCEED_SYSTEM_MESSAGE;
        };
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", systemMessage));
        addGptMessages(questionAndAnswers, gptMessages);

        return gptMessages;
    }

    public static List<GptMessage> createGptEndMessages(QuestionAndAnswers questionAndAnswers) {
        String systemMessage = switch (questionAndAnswers.getInterview().getInterviewType()) {
            case LIVE_CODING -> GptSystemMessageConstant.CODING_END_SYSTEM_MESSAGE;
            case PERSONALITY -> GptSystemMessageConstant.PERSONALITY_END_SYSTEM_MESSAGE;
            case CATEGORY_BASED, RESUME_BASED -> GptSystemMessageConstant.END_SYSTEM_MESSAGE;
        };
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", systemMessage));
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
