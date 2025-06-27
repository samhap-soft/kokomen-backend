package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.external.dto.request.Message;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

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

    public static SystemContentBlock createBedrockProceedSystemMessage() {
        return SystemContentBlock.builder()
                .text(GptSystemMessageConstant.PROCEED_SYSTEM_MESSAGE)
                .build();
    }

    public static SystemContentBlock createBedrockEndSystemMessage() {
        return SystemContentBlock.builder()
                .text(GptSystemMessageConstant.END_SYSTEM_MESSAGE)
                .build();
    }

    public static List<software.amazon.awssdk.services.bedrockruntime.model.Message> createBedrockMessages(QuestionAndAnswers questionAndAnswers) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new ArrayList<>();
        messages.add(createBedrockMessage("user", "면접을 시작합니다."));
        addBedrockMessages(questionAndAnswers, messages);
        return messages;
    }

    private static void addBedrockMessages(QuestionAndAnswers questionAndAnswers, List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages) {
        questionAndAnswers.getPrevAnswers().forEach(answer -> {
            messages.add(createBedrockMessage("assistant", answer.getQuestion().getContent()));
            messages.add(createBedrockMessage("user", answer.getContent()));
        });

        messages.add(createBedrockMessage("assistant", questionAndAnswers.readCurQuestion().getContent()));
        messages.add(createBedrockMessage("user", questionAndAnswers.getCurAnswerContent()));
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Message createBedrockMessage(String role, String content) {
        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(role)
                .content(List.of(ContentBlock.builder().text(content).build()))
                .build();
    }
}
