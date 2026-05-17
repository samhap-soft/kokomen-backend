package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.tool.InterviewBedrockSystemMessageConstant;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public final class InterviewBedrockRequestFactory {

    public static final String PROCEED_TOOL_NAME = "submit_interview_proceed";
    public static final String END_TOOL_NAME = "submit_interview_end";
    public static final String ANSWER_FEEDBACK_TOOL_NAME = "submit_answer_feedback";

    private InterviewBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createProceedSystem() {
        return List.of(SystemContentBlock.builder()
                .text(InterviewBedrockSystemMessageConstant.IN_PROGRESS_RANK_AND_NEXT_QUESTION_PROMPT)
                .build());
    }

    public static List<SystemContentBlock> createEndSystem() {
        return List.of(SystemContentBlock.builder()
                .text(InterviewBedrockSystemMessageConstant.END_PROMPT)
                .build());
    }

    public static List<SystemContentBlock> createAnswerFeedbackSystem() {
        return List.of(SystemContentBlock.builder()
                .text(InterviewBedrockSystemMessageConstant.ANSWER_FEEDBACK_PROMPT)
                .build());
    }

    public static List<Message> createProceedMessages(QuestionAndAnswers questionAndAnswers) {
        return createInterviewHistoryMessages(questionAndAnswers);
    }

    public static List<Message> createAnswerFeedbackMessages(QuestionAndAnswers questionAndAnswers, AnswerRank curAnswerRank) {
        List<Message> messages = createInterviewHistoryMessages(questionAndAnswers);
        messages.add(textMessage("user",
                "가장 최근 답변에 대해 매겨진 answer_rank는 " + curAnswerRank.name() + " 입니다. 위 답변에 대한 피드백을 작성해 주세요."));
        return messages;
    }

    public static ToolConfiguration createProceedToolConfig() {
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "rank", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "enum", Document.fromList(rankEnumDocs()),
                                "description", Document.fromString("답변에 대한 평가 등급. A, B, C, D, F 중 한 글자."))),
                        "next_question", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("이전 질문/답변을 기반으로 한 다음 꼬리 질문 1문장."))))),
                "required", Document.fromList(List.of(
                        Document.fromString("rank"),
                        Document.fromString("next_question")))));

        return buildToolConfig(PROCEED_TOOL_NAME, "면접 답변에 대한 rank와 다음 꼬리 질문을 함께 제출한다.", schema);
    }

    public static ToolConfiguration createEndToolConfig() {
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "rank", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "enum", Document.fromList(rankEnumDocs()),
                                "description", Document.fromString("가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자."))),
                        "feedback", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("가장 최근 답변에 대한 3-4문장 피드백. 존댓말, 점수/랭크 미언급."))),
                        "total_feedback", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("전체 면접에 대한 3-4문장 종합 피드백. 존댓말, 점수/랭크 미언급."))))),
                "required", Document.fromList(List.of(
                        Document.fromString("rank"),
                        Document.fromString("feedback"),
                        Document.fromString("total_feedback")))));

        return buildToolConfig(END_TOOL_NAME, "면접 종료 시점의 rank와 마지막 답변/전체 피드백을 함께 제출한다.", schema);
    }

    public static ToolConfiguration createAnswerFeedbackToolConfig() {
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "feedback", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("가장 최근 답변에 대한 3-4문장 피드백. 존댓말, 점수/랭크 미언급."))))),
                "required", Document.fromList(List.of(Document.fromString("feedback")))));

        return buildToolConfig(ANSWER_FEEDBACK_TOOL_NAME, "가장 최근 답변에 대한 피드백을 제출한다.", schema);
    }

    private static List<Message> createInterviewHistoryMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        List<Question> questions = questionAndAnswers.getQuestions();
        List<Answer> prevAnswers = questionAndAnswers.getPrevAnswers();
        for (int i = 0; i < prevAnswers.size(); i++) {
            messages.add(textMessage("assistant", questions.get(i).getContent()));
            messages.add(textMessage("user", prevAnswers.get(i).getContent()));
        }
        messages.add(textMessage("assistant", questionAndAnswers.readCurQuestion().getContent()));
        messages.add(textMessage("user", questionAndAnswers.getCurAnswerContent()));
        return messages;
    }

    private static Message textMessage(String role, String content) {
        return Message.builder()
                .role(role)
                .content(List.of(ContentBlock.builder().text(content).build()))
                .build();
    }

    private static List<Document> rankEnumDocs() {
        return List.of(
                Document.fromString(AnswerRank.A.name()),
                Document.fromString(AnswerRank.B.name()),
                Document.fromString(AnswerRank.C.name()),
                Document.fromString(AnswerRank.D.name()),
                Document.fromString(AnswerRank.F.name()));
    }

    private static ToolConfiguration buildToolConfig(String toolName, String description, Document schema) {
        Tool tool = Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(toolName)
                        .description(description)
                        .inputSchema(ToolInputSchema.builder().json(schema).build())
                        .build())
                .build();

        return ToolConfiguration.builder()
                .tools(tool)
                .toolChoice(ToolChoice.builder()
                        .tool(SpecificToolChoice.builder().name(toolName).build())
                        .build())
                .build();
    }
}
