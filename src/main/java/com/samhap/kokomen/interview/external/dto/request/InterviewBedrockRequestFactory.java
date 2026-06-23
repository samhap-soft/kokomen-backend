package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.tool.CodingInterviewBedrockSystemMessageConstant;
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

    public static List<SystemContentBlock> createProceedSystem(InterviewType interviewType) {
        String prompt = interviewType == InterviewType.LIVE_CODING
                ? CodingInterviewBedrockSystemMessageConstant.CODING_IN_PROGRESS_RANK_AND_NEXT_QUESTION_PROMPT
                : InterviewBedrockSystemMessageConstant.IN_PROGRESS_RANK_AND_NEXT_QUESTION_PROMPT;
        return List.of(SystemContentBlock.builder()
                .text(prompt)
                .build());
    }

    public static List<SystemContentBlock> createEndSystem(InterviewType interviewType) {
        String prompt = interviewType == InterviewType.LIVE_CODING
                ? CodingInterviewBedrockSystemMessageConstant.CODING_END_PROMPT
                : InterviewBedrockSystemMessageConstant.END_PROMPT;
        return List.of(SystemContentBlock.builder()
                .text(prompt)
                .build());
    }

    public static List<SystemContentBlock> createAnswerFeedbackSystem(InterviewType interviewType,
                                                                      AnswerRank curAnswerRank) {
        String prompt = interviewType == InterviewType.LIVE_CODING
                ? CodingInterviewBedrockSystemMessageConstant.CODING_ANSWER_FEEDBACK_PROMPT
                : InterviewBedrockSystemMessageConstant.ANSWER_FEEDBACK_PROMPT;
        return List.of(
                SystemContentBlock.builder()
                        .text(prompt)
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
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "reasoning", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString(
                                        "답변 분석과 다음 질문 설계 근거를 정리한 사고 과정. "
                                                + "answer_analysis(답변 평가 근거)와 question_planning(다음 질문 의도)을 포함."))),
                        "rank", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "enum", Document.fromList(rankEnumDocs()),
                                "description", Document.fromString("답변에 대한 평가 등급. A, B, C, D, F 중 한 글자."))),
                        "next_question", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("이전 질문/답변을 기반으로 한 다음 꼬리 질문 1문장."))))),
                "required", Document.fromList(List.of(
                        Document.fromString("reasoning"),
                        Document.fromString("rank"),
                        Document.fromString("next_question")))));

        return buildToolConfig(PROCEED_TOOL_NAME, "면접 답변에 대한 rank와 다음 꼬리 질문을 함께 제출한다.", schema);
    }

    public static ToolConfiguration createEndToolConfig() {
        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "reasoning", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString(
                                        "마지막 답변 평가 근거와 전체 면접 종합 평가 근거를 정리한 사고 과정. "
                                                + "last_answer_analysis와 strengths/improvements/learning_direction 정리를 포함."))),
                        "rank", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "enum", Document.fromList(rankEnumDocs()),
                                "description", Document.fromString("가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자."))),
                        "feedback", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("가장 최근 답변에 대한 3-4문장 피드백. 존댓말, 점수/랭크 미언급."))),
                        "strengths", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("면접자의 강점 1-2문장. 존댓말, 점수/랭크 미언급."))),
                        "improvements", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("보완·개선 영역 1-2문장. 존댓말, 점수/랭크 미언급."))),
                        "learning_direction", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("향후 학습 방향 1-2문장. 존댓말, 점수/랭크 미언급."))))),
                "required", Document.fromList(List.of(
                        Document.fromString("reasoning"),
                        Document.fromString("rank"),
                        Document.fromString("feedback"),
                        Document.fromString("strengths"),
                        Document.fromString("improvements"),
                        Document.fromString("learning_direction")))));

        return buildToolConfig(END_TOOL_NAME, "면접 종료 시점의 rank와 마지막 답변 피드백·전체 종합 평가를 함께 제출한다.", schema);
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
