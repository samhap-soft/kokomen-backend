package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
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

public final class ResumeBedrockRequestFactory {

    public static final String QUESTION_GENERATION_TOOL_NAME = "submit_resume_questions";
    public static final String EVALUATION_TOOL_NAME = "submit_resume_evaluation";

    private ResumeBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createQuestionGenerationSystem() {
        return List.of(SystemContentBlock.builder()
                .text(ResumeBedrockSystemMessageConstant.QUESTION_GENERATION_PROMPT)
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
                                "description", Document.fromString("이 질문을 선택한 이유."))))),
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

    public static List<SystemContentBlock> createEvaluationSystem() {
        return List.of(SystemContentBlock.builder()
                .text(ResumeBedrockSystemMessageConstant.EVALUATION_PROMPT)
                .build());
    }

    public static List<Message> createEvaluationMessages(ResumeEvaluationRequest request) {
        String userText = """
                <resume>
                %s
                </resume>

                <portfolio>
                %s
                </portfolio>

                <target_position>
                %s
                </target_position>

                <job_requirements>
                %s
                </job_requirements>

                <job_career>
                %s
                </job_career>
                """.formatted(
                nullToEmpty(request.resume()),
                nullToEmpty(request.portfolio()),
                nullToEmpty(request.jobPosition()),
                nullToEmpty(request.jobDescription()),
                nullToEmpty(request.jobCareer()));

        return List.of(Message.builder()
                .role("user")
                .content(List.of(ContentBlock.builder().text(userText).build()))
                .build());
    }

    public static ToolConfiguration createEvaluationToolConfig() {
        Document categorySchema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "reasoning", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("점수 산정 전 사고 과정. 카테고리에 한정된 근거만 작성."))),
                        "score", Document.fromMap(Map.of(
                                "type", Document.fromString("integer"),
                                "minimum", Document.fromNumber(0),
                                "maximum", Document.fromNumber(100),
                                "description", Document.fromString("0-100 점수. score_anchors 기준."))),
                        "reason", bulletArraySchema("평가 이유 항목들. 각 항목은 한 문장."),
                        "improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 한 문장."))),
                "required", Document.fromList(List.of(
                        Document.fromString("reasoning"),
                        Document.fromString("score"),
                        Document.fromString("reason"),
                        Document.fromString("improvements")))));

        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.ofEntries(
                        Map.entry("technical_skills", categorySchema),
                        Map.entry("project_experience", categorySchema),
                        Map.entry("problem_solving", categorySchema),
                        Map.entry("career_growth", categorySchema),
                        Map.entry("documentation", categorySchema),
                        Map.entry("total_feedback", Document.fromMap(Map.of(
                                "type", Document.fromString("string"),
                                "description", Document.fromString("종합 총평. 강점·개선·학습 방향 포함, 한 단락.")))))),
                "required", Document.fromList(List.of(
                        Document.fromString("technical_skills"),
                        Document.fromString("project_experience"),
                        Document.fromString("problem_solving"),
                        Document.fromString("career_growth"),
                        Document.fromString("documentation"),
                        Document.fromString("total_feedback")))));

        return buildToolConfig(EVALUATION_TOOL_NAME, "이력서/포트폴리오 종합 평가를 제출한다.", schema);
    }

    private static Document bulletArraySchema(String description) {
        return Document.fromMap(Map.of(
                "type", Document.fromString("array"),
                "items", Document.fromMap(Map.of("type", Document.fromString("string"))),
                "minItems", Document.fromNumber(2),
                "maxItems", Document.fromNumber(6),
                "description", Document.fromString(description)));
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
