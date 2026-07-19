package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.tool.ResumeSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeToolNames;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public static final String QUESTION_GENERATION_TOOL_NAME = ResumeToolNames.QUESTION_GENERATION;
    public static final String EVALUATION_TOOL_NAME = ResumeToolNames.EVALUATION;

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

    public static List<SystemContentBlock> createEvaluationSystem() {
        return List.of(SystemContentBlock.builder()
                .text(ResumeSystemMessages.evaluation())
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
        // 중첩 object는 Claude XML 누수를 유발하므로 5개 카테고리를 flat 필드로 펼친다.
        Map<String, Document> properties = new LinkedHashMap<>();
        List<Document> required = new ArrayList<>();
        for (String category : ResumeEvaluationSchema.CATEGORIES) {
            putCategoryFields(properties, required, category);
        }
        properties.put("total_feedback", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "description", Document.fromString("종합 총평. 강점·개선·학습 방향 포함, 한 단락."))));
        required.add(Document.fromString("total_feedback"));

        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(properties),
                "required", Document.fromList(required)));

        return buildToolConfig(EVALUATION_TOOL_NAME, "이력서/포트폴리오 종합 평가를 제출한다.", schema);
    }

    private static void putCategoryFields(Map<String, Document> properties, List<Document> required, String category) {
        properties.put(category + "_reasoning", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "description", Document.fromString("이 카테고리 점수 산정 전 사고 과정. 카테고리에 한정된 근거만 작성."))));
        properties.put(category + "_score", Document.fromMap(Map.of(
                "type", Document.fromString("integer"),
                "minimum", Document.fromNumber(ResumeEvaluationSchema.SCORE_MIN),
                "maximum", Document.fromNumber(ResumeEvaluationSchema.SCORE_MAX),
                "description", Document.fromString("0-100 점수. score_anchors 기준."))));
        properties.put(category + "_reason", bulletArraySchema("평가 이유 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        properties.put(category + "_improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        required.add(Document.fromString(category + "_reasoning"));
        required.add(Document.fromString(category + "_score"));
        required.add(Document.fromString(category + "_reason"));
        required.add(Document.fromString(category + "_improvements"));
    }

    private static Document bulletArraySchema(String description) {
        return Document.fromMap(Map.of(
                "type", Document.fromString("array"),
                "items", Document.fromMap(Map.of("type", Document.fromString("string"))),
                "minItems", Document.fromNumber(ResumeEvaluationSchema.BULLET_MIN_ITEMS),
                "maxItems", Document.fromNumber(ResumeEvaluationSchema.BULLET_MAX_ITEMS),
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
