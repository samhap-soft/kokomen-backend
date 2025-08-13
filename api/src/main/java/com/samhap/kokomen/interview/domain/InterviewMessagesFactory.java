package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.request.GptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

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

    public static List<Message> createBedrockMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        messages.add(createBedrockMessage("user", "면접을 시작합니다."));
        addBedrockMessages(questionAndAnswers, messages);
        return messages;
    }

    private static void addBedrockMessages(QuestionAndAnswers questionAndAnswers, List<Message> messages) {
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            messages.add(createBedrockMessage("assistant", questions.get(i).getContent()));
            messages.add(createBedrockMessage("user", prevAnswers.get(i).getContent()));
        }

        messages.add(createBedrockMessage("assistant", questionAndAnswers.readCurQuestion().getContent()));
        messages.add(createBedrockMessage("user", questionAndAnswers.getCurAnswerContent()));
    }

    private static Message createBedrockMessage(String role, String content) {
        return Message.builder()
                .role(role)
                .content(List.of(ContentBlock.builder().text(content).build()))
                .build();
    }

    public static Document createInterviewProceedBedrockFlowDocument(QuestionAndAnswers questionAndAnswers) {
        List<Document> interviewHistoryDocuments = new ArrayList<>();
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            interviewHistoryDocuments.add(createQuestionAndAnswerDocument(questions.get(i).getContent(), prevAnswers.get(i).getContent()));
        }
        interviewHistoryDocuments.add(createQuestionAndAnswerDocument(
                questionAndAnswers.readCurQuestion().getContent(), questionAndAnswers.getCurAnswerContent()));

        return Document.fromMap(Map.of("interview_history", Document.fromList(interviewHistoryDocuments)));
    }

    private static Document createQuestionAndAnswerDocument(String question, String answer) {
        return Document.fromMap(Map.of(
                "question", Document.fromString(question),
                "answer", Document.fromString(answer)));
    }

    public static Document createAnswerFeedbackBedrockFlowDocument(QuestionAndAnswers questionAndAnswers, AnswerRank curAnswerRank) {
        List<Document> interviewHistoryDocuments = new ArrayList<>();
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            Answer prevAnswer = prevAnswers.get(i);
            interviewHistoryDocuments.add(createQuestionAndAnswerDocument(questions.get(i).getContent(), prevAnswer.getContent(), prevAnswer.getAnswerRank()));
        }
        interviewHistoryDocuments.add(createQuestionAndAnswerDocument(
                questionAndAnswers.readCurQuestion().getContent(), questionAndAnswers.getCurAnswerContent(), curAnswerRank));

        return Document.fromMap(Map.of("interview_history", Document.fromList(interviewHistoryDocuments)));
    }

    private static Document createQuestionAndAnswerDocument(String question, String answer, AnswerRank answerRank) {
        return Document.fromMap(Map.of(
                "question", Document.fromString(question),
                "answer", Document.fromString(answer),
                "answer_rank", Document.fromString(answerRank.name())));
    }
}
