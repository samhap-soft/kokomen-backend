package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.domain.ResumeAnalysisDimension;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import com.samhap.kokomen.resume.tool.ResumeAnalysisUserMessages;
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

/**
 * 신규 이력서 분석 Bedrock 요청 팩토리. 구 ResumeBedrockRequestFactory는 0바이트 수정 대상이므로
 * 그 클래스의 private 헬퍼(bulletArraySchema·buildToolConfig)를 재사용하지 않고 같은 형태로 복사했다
 * (가시성 확대는 D2 위반).
 */
public final class ResumeAnalysisBedrockRequestFactory {

    private ResumeAnalysisBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createEvaluationSystem(boolean jdProvided) {
        return List.of(SystemContentBlock.builder()
                .text(ResumeAnalysisSystemMessages.evaluation(jdProvided))
                .build());
    }

    public static List<Message> createEvaluationMessages(ResumeAnalysisCommand command) {
        String userText = ResumeAnalysisUserMessages.evaluation(command.jdProvided(), command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobDescription(), command.jobCareer());

        return List.of(Message.builder()
                .role("user")
                .content(List.of(ContentBlock.builder().text(userText).build()))
                .build());
    }

    public static ToolConfiguration createEvaluationToolConfig(boolean jdProvided) {
        // 중첩 object는 Claude XML 누수를 유발하므로 차원별 4필드를 flat으로 펼친다.
        // required를 느슨하게 풀어 모델이 알아서 빼게 하지 않고, jdProvided에 따라 필드 자체를 넣지 않는다.
        Map<String, Document> properties = new LinkedHashMap<>();
        List<Document> required = new ArrayList<>();
        for (ResumeAnalysisDimension dimension : ResumeAnalysisSchema.dimensions(jdProvided)) {
            putDimensionFields(properties, required, dimension);
        }
        properties.put("total_feedback", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "description", Document.fromString("종합 총평. 강점·개선·학습 방향 포함, 한 단락."))));
        required.add(Document.fromString("total_feedback"));

        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(properties),
                "required", Document.fromList(required)));

        return buildToolConfig(ResumeAnalysisToolNames.EVALUATION,
                "이력서/포트폴리오 종합 평가를 제출한다.", schema);
    }

    private static void putDimensionFields(Map<String, Document> properties, List<Document> required,
                                           ResumeAnalysisDimension dimension) {
        String key = dimension.toolKey();
        properties.put(key + "_reasoning", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "description", Document.fromString("이 차원 점수 산정 전 사고 과정. 이 차원에 한정된 근거만 작성."))));
        properties.put(key + "_score", Document.fromMap(Map.of(
                "type", Document.fromString("integer"),
                "minimum", Document.fromNumber(ResumeAnalysisSchema.SCORE_MIN),
                "maximum", Document.fromNumber(ResumeAnalysisSchema.SCORE_MAX),
                "description", Document.fromString(ResumeAnalysisSchema.scoreDescription(dimension)))));
        properties.put(key + "_reason", bulletArraySchema("평가 이유 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        properties.put(key + "_improvements", bulletArraySchema("보완 사항 항목들. 각 항목은 정보 밀도 높은 1-2문장."));
        required.add(Document.fromString(key + "_reasoning"));
        required.add(Document.fromString(key + "_score"));
        required.add(Document.fromString(key + "_reason"));
        required.add(Document.fromString(key + "_improvements"));
    }

    public static List<SystemContentBlock> createQuestionGenerationSystem() {
        return List.of(SystemContentBlock.builder()
                .text(ResumeAnalysisSystemMessages.questionGeneration())
                .build());
    }

    public static List<Message> createQuestionGenerationMessages(ResumeAnalysisQuestionCallCommand command) {
        String userText = ResumeAnalysisUserMessages.questionGeneration(command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobCareer(), command.evaluationResult());

        return List.of(Message.builder()
                .role("user")
                .content(List.of(ContentBlock.builder().text(userText).build()))
                .build());
    }

    public static ToolConfiguration createQuestionGenerationToolConfig() {
        Map<String, Document> itemProperties = new LinkedHashMap<>();
        itemProperties.put("question", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "maxLength", Document.fromNumber(ResumeAnalysisSchema.QUESTION_MAX_LENGTH),
                "description", Document.fromString(
                        "질문 내용. " + ResumeAnalysisSchema.QUESTION_MAX_LENGTH + "자 이내."))));
        itemProperties.put("reason", Document.fromMap(Map.of(
                "type", Document.fromString("string"),
                "maxLength", Document.fromNumber(ResumeAnalysisSchema.QUESTION_REASON_MAX_LENGTH),
                "description", Document.fromString(
                        "질문 선정 이유. " + ResumeAnalysisSchema.QUESTION_REASON_MAX_LENGTH + "자 이내."))));

        Document questionItemSchema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(itemProperties),
                "required", Document.fromList(List.of(
                        Document.fromString("question"),
                        Document.fromString("reason")))));

        Document schema = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(Map.of(
                        "questions", Document.fromMap(Map.of(
                                "type", Document.fromString("array"),
                                "items", questionItemSchema,
                                "minItems", Document.fromNumber(ResumeAnalysisSchema.QUESTION_MIN_ITEMS),
                                "maxItems", Document.fromNumber(ResumeAnalysisSchema.QUESTION_MAX_ITEMS),
                                "description", Document.fromString(
                                        "이력서/포트폴리오 기반 면접 질문 목록. 정확히 5-7개."))))),
                "required", Document.fromList(List.of(Document.fromString("questions")))));

        return buildToolConfig(ResumeAnalysisToolNames.QUESTION_GENERATION,
                "이력서/포트폴리오 기반 면접 질문 목록을 제출한다.", schema);
    }

    private static Document bulletArraySchema(String description) {
        return Document.fromMap(Map.of(
                "type", Document.fromString("array"),
                "items", Document.fromMap(Map.of("type", Document.fromString("string"))),
                "minItems", Document.fromNumber(ResumeAnalysisSchema.BULLET_MIN_ITEMS),
                "maxItems", Document.fromNumber(ResumeAnalysisSchema.BULLET_MAX_ITEMS),
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
}
