package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.external.bedrock.BedrockToolSchemaRenderer;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.tool.InterviewSystemMessageBuilder;
import com.samhap.kokomen.interview.tool.InterviewToolSchemas;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

public final class InterviewBedrockRequestFactory {

    public static final String PROCEED_TOOL_NAME = InterviewToolSchemas.PROCEED_TOOL_NAME;
    public static final String END_TOOL_NAME = InterviewToolSchemas.END_TOOL_NAME;
    public static final String ANSWER_FEEDBACK_TOOL_NAME = InterviewToolSchemas.ANSWER_FEEDBACK_TOOL_NAME;

    private InterviewBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createProceedSystem(InterviewType interviewType) {
        // Bedrock은 진행 단계에서 rank/next_question만 생성하고 feedback은 별도 콜로 받으므로 feedbackInline=false
        return List.of(SystemContentBlock.builder()
                .text(InterviewSystemMessageBuilder.proceed(interviewType, false))
                .build());
    }

    public static List<SystemContentBlock> createEndSystem(InterviewType interviewType) {
        return List.of(SystemContentBlock.builder()
                .text(InterviewSystemMessageBuilder.end(interviewType))
                .build());
    }

    public static List<SystemContentBlock> createAnswerFeedbackSystem(InterviewType interviewType,
                                                                      AnswerRank curAnswerRank) {
        return List.of(
                SystemContentBlock.builder()
                        .text(InterviewSystemMessageBuilder.answerFeedback(interviewType))
                        .build(),
                SystemContentBlock.builder()
                        .text("<context>대상 답변 rank: " + curAnswerRank.name() + "</context>")
                        .build());
    }

    public static List<Message> createProceedMessages(QuestionAndAnswers questionAndAnswers) {
        return createInterviewHistoryMessages(questionAndAnswers);
    }

    public static List<Message> createAnswerFeedbackMessages(QuestionAndAnswers questionAndAnswers) {
        return createInterviewHistoryMessages(questionAndAnswers);
    }

    public static ToolConfiguration createProceedToolConfig() {
        // Bedrock 진행 단계는 feedback을 별도 콜로 받으므로 feedbackInline=false
        return BedrockToolSchemaRenderer.render(InterviewToolSchemas.proceed(false));
    }

    public static ToolConfiguration createEndToolConfig() {
        return BedrockToolSchemaRenderer.render(InterviewToolSchemas.end());
    }

    public static ToolConfiguration createAnswerFeedbackToolConfig() {
        return BedrockToolSchemaRenderer.render(InterviewToolSchemas.answerFeedback());
    }

    private static List<Message> createInterviewHistoryMessages(QuestionAndAnswers questionAndAnswers) {
        return questionAndAnswers.toConversationTurns().stream()
                .map(turn -> textMessage(turn.role(), turn.content()))
                .toList();
    }

    private static Message textMessage(String role, String content) {
        return Message.builder()
                .role(role)
                .content(List.of(ContentBlock.builder().text(content).build()))
                .build();
    }
}
