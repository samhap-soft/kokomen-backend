package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import java.util.ArrayList;
import java.util.List;

public final class InterviewMessagesFactory {

    private InterviewMessagesFactory() {
    }

    public static List<GptMessage> createGptProceedMessages(QuestionAndAnswers questionAndAnswers) {
        // GPT는 한 번의 호출로 feedback까지 생성하므로 feedbackInline=true
        String systemMessage = InterviewSystemMessageBuilder.proceed(
                questionAndAnswers.getInterview().getInterviewType(), true);
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", systemMessage));
        addGptMessages(questionAndAnswers, gptMessages);

        return gptMessages;
    }

    public static List<GptMessage> createGptEndMessages(QuestionAndAnswers questionAndAnswers) {
        String systemMessage = InterviewSystemMessageBuilder.end(
                questionAndAnswers.getInterview().getInterviewType());
        List<GptMessage> gptMessages = new ArrayList<>();
        gptMessages.add(new GptMessage("system", systemMessage));
        addGptMessages(questionAndAnswers, gptMessages);

        return gptMessages;
    }

    private static void addGptMessages(QuestionAndAnswers questionAndAnswers, List<GptMessage> gptMessages) {
        for (ConversationTurn turn : questionAndAnswers.toConversationTurns()) {
            gptMessages.add(new GptMessage(turn.role(), turn.content()));
        }
    }
}
