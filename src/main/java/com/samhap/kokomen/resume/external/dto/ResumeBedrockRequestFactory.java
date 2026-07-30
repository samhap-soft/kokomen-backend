package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.tool.ResumeSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeToolNames;
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

/**
 * 이력서 기반 질문 생성(구 플로우, Task 9에서 삭제 예정) Bedrock 요청 팩토리.
 * 평가 관련 팩토리 메서드는 {@link ResumeAnalysisBedrockRequestFactory}로 이전됐다.
 */
public final class ResumeBedrockRequestFactory {

    public static final String QUESTION_GENERATION_TOOL_NAME = ResumeToolNames.QUESTION_GENERATION;

    private ResumeBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createQuestionGenerationSystem() {
        return List.of(SystemContentBlock.builder()
                .text(ResumeSystemMessages.questionGeneration())
                .build());
    }

    public static List<Message> createQuestionGenerationMessages(String resumeText, String portfolioText, String jobCareer) {
        String userText = """
                <resume>
                %s
                </resume>

                <portfolio>
                %s
                </portfolio>

                <job_career>
                %s
                </job_career>
                """.formatted(
                nullToEmpty(resumeText),
                nullToEmpty(portfolioText),
                nullToEmpty(jobCareer));

        return List.of(Message.builder()
                .role("user")
                .content(List.of(ContentBlock.builder().text(userText).build()))
                .build());
    }

    public static ToolConfiguration createQuestionGenerationToolConfig() {
        Document questionItemSchema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "question", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("기술 면접에서 물어볼 질문 1문장."))),
                        "reason", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString(
                                        "이 질문이 겨냥하는 이력서/포트폴리오의 구체적 항목·문장과, 이 질문으로 검증하려는 역량."))))),
                "required", Document.fromList(List.of(
                        Document.fromString("question"),
                        Document.fromString("reason")))));

        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "questions", Document.fromMap(Map.of(
                                "type", Document.fromString("array"),
                                "items", questionItemSchema,
                                "minItems", Document.fromNumber(5),
                                "maxItems", Document.fromNumber(7),
                                "description", Document.fromString("이력서/포트폴리오 기반 면접 질문 목록. 정확히 5-7개."))))),
                "required", Document.fromList(List.of(Document.fromString("questions")))));

        return buildToolConfig(QUESTION_GENERATION_TOOL_NAME,
                "이력서/포트폴리오 기반 면접 질문 목록을 제출한다.", schema);
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

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
